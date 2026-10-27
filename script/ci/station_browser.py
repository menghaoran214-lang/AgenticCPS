"""Browser-only acceptance against the disposable real HTTP server; no API response mocks."""
import json
from contextlib import contextmanager
import os
import time
from pathlib import Path
from urllib.parse import urlsplit
import cv2
from playwright.sync_api import sync_playwright
from station_journey import BASE, WORK, AGENT, WEB_BUYER, PHONES, connect, query, require_isolated


def login_form(page, member, expect_binding=False):
    page.get_by_text('账号登录', exact=True).first.click()
    inputs = page.locator('.login-wrap input.uni-input-input')
    inputs.nth(0).fill(PHONES[member])
    try:
        page.locator('.login-wrap input[type="password"]').fill(os.environ['CPS_STATION_PASSWORD'])
    except Exception:
        raise RuntimeError('Password field could not be filled') from None
    page.locator('.agreement-option uni-radio').first.click()
    with page.expect_response(lambda response: '/member/auth/login' in response.url) as login:
        if expect_binding:
            with page.expect_response(lambda response: '/cps/promoter/bind' in response.url) as binding:
                page.locator('.login-btn-start').click()
            result = binding.value.json()
            assert result['code'] == 0 and result['data'] is True
        else:
            page.locator('.login-btn-start').click()
    assert login.value.json()['code'] == 0
    page.locator('.login-wrap').wait_for(state='hidden', timeout=30000)


@contextmanager
def capture_failure(browser, diagnostics):
    try:
        yield
    except Exception:
        state = {'events': diagnostics, 'pages': []}
        for index, context in enumerate(browser.contexts):
            for number, page in enumerate(context.pages):
                try:
                    state['pages'].append({'url': page.url,
                        'title': page.title(), 'text': page.locator('body').inner_text(timeout=3000)[:4000],
                        'storage_keys': page.evaluate('Object.keys(localStorage)'),
                        'inputs': page.locator('input').evaluate_all('(nodes) => nodes.map(node => ({type:node.type,className:node.className,placeholder:node.placeholder}))')})
                    page.screenshot(path=str(WORK / f'evidence/browser-failure-{index}-{number}.png'), full_page=True, timeout=5000)
                except Exception:
                    pass
        (WORK / 'evidence/browser-diagnostics.json').write_text(
            json.dumps(state, ensure_ascii=False, indent=2), encoding='utf-8')
        print('BROWSER_DIAGNOSTICS ' + json.dumps(state, ensure_ascii=False))
        raise


def run():
    require_isolated(BASE, os.environ.get('CPS_STATION_ISOLATED'))
    connection = connect()
    evidence = WORK / 'evidence'
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(channel=os.environ.get('CPS_STATION_BROWSER_CHANNEL'))
        diagnostics = []
        def context():
            result = browser.new_context(viewport={'width': 390, 'height': 844}, is_mobile=True, has_touch=True)
            def observe(page):
                page.on('pageerror', lambda error: diagnostics.append({'page_error': str(error)[:1500]}))
                page.on('response', lambda response: diagnostics.append(
                    {'path': urlsplit(response.url).path, 'status': response.status})
                    if urlsplit(response.url).hostname == '127.0.0.1' else None)
            result.on('page', observe)
            # Block all third-party browser traffic, including images and telemetry.
            result.route('**/*', lambda route: route.continue_()
                         if urlsplit(route.request.url).hostname == '127.0.0.1' else route.abort())
            return result

        with capture_failure(browser, diagnostics):
            station = context()
            page = station.new_page()
            page.goto(BASE + '/h5/pages/cps/promoter/invite?tenantId=1')
            page.locator('.login-wrap').wait_for(state='visible', timeout=30000)
            login_form(page, AGENT)
            with page.expect_response(lambda response: '/cps/promoter/summary' in response.url) as summary:
                page.goto(BASE + '/h5/pages/cps/promoter/invite?tenantId=1')
            code = summary.value.json()['data']['inviteCode']
            box = page.locator('.qr-box')
            box.wait_for(state='visible')
            qr_file = evidence / 'station-qr.png'
            decoded = ''
            deadline = time.monotonic() + 20
            while not decoded and time.monotonic() < deadline:
                box.screenshot(path=str(qr_file))
                decoded, _, _ = cv2.QRCodeDetector().detectAndDecode(cv2.imread(str(qr_file)))
                if not decoded:
                    page.wait_for_timeout(500)
            assert decoded == BASE + '/h5/pages/cps/goods?inviteCode=' + code, 'Rendered QR did not decode to the invite link'
            page.screenshot(path=str(evidence / 'station-invite.png'), full_page=True)

            consumer = context()
            buyer = consumer.new_page()
            buyer.goto(decoded)
            # Wait for the real page to persist the invitation before opening a protected page.
            buyer.wait_for_function("""() => Object.entries(localStorage).some(([key,value]) =>
                key.includes('cps_pending_invite') && value.includes('code'))""")
            buyer.goto(BASE + '/h5/pages/cps/promoter/index')
            buyer.locator('.login-wrap').wait_for(state='visible', timeout=30000)
            login_form(buyer, WEB_BUYER, expect_binding=True)
            rows = query(connection, 'SELECT parent_member_id FROM cps_promoter_relation WHERE member_id=%s AND tenant_id=1', (WEB_BUYER,))
            assert rows and rows[0]['parent_member_id'] == AGENT
            with buyer.expect_response(lambda response: '/cps/promoter/summary' in response.url) as bound_summary:
                buyer.goto(BASE + '/h5/pages/cps/promoter/index')
            assert bound_summary.value.json()['data']['parentMemberId'] == AGENT
            buyer.screenshot(path=str(evidence / 'consumer-bound.png'), full_page=True)
            with page.expect_response(lambda response: '/cps/promoter/team-orders/page' in response.url) as orders:
                page.goto(BASE + '/h5/pages/cps/promoter/orders')
            assert orders.value.json()['code'] == 0
            page.get_by_text('CI Station Order', exact=True).first.wait_for(timeout=10000)
            page.screenshot(path=str(evidence / 'station-orders.png'), full_page=True)
            browser.close()
    connection.close()
    (evidence / 'browser.json').write_text(json.dumps({
        'passed': ['Real password login form', 'Rendered QR decoded and correct H5 subdirectory preserved',
                   'Guest invite persisted', 'Real login triggered authenticated binding',
                   'Stored recommendation points to the station'],
        'external_network': 'blocked',
        'not_tested': ['physical phone camera', 'real affiliate provider', 'real payment'],
    }, indent=2), encoding='utf-8')
    print('PASS: rendered QR, real browser login and station binding')


if __name__ == '__main__':
    run()

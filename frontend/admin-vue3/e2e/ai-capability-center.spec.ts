import { test, expect, type Page } from '@playwright/test'

async function bootstrap(page: Page) {
  await page.addInitScript(() => {
    const put = (key: string, value: unknown) =>
      localStorage.setItem(
        key,
        JSON.stringify({ c: Date.now(), e: Date.now() + 3600000, v: JSON.stringify(value) })
      )
    put('ACCESS_TOKEN', 'capability-e2e')
    put('REFRESH_TOKEN', 'capability-e2e-refresh')
    put('tenantId', 1)
  })
  await page.route('**/admin-api/**', async (route) => {
    const url = new URL(route.request().url())
    const data = url.pathname.endsWith('/get-permission-info')
      ? {
          user: { id: 1, nickname: '测试运营', avatar: '', deptId: 1 },
          roles: ['admin'],
          permissions: ['*:*:*'],
          menus: [
            {
              id: 9990,
              name: 'AI 测试',
              type: 1,
              path: '/capability-test',
              component: null,
              visible: true,
              children: [
                {
                  id: 9991,
                  name: 'AI 对话',
                  type: 2,
                  path: 'chat',
                  component: 'ai/chat/index/index',
                  componentName: 'AiChat',
                  visible: true
                },
                {
                  id: 9992,
                  name: '角色管理',
                  type: 2,
                  path: 'roles',
                  component: 'ai/model/chatRole/index',
                  componentName: 'AiChatRole',
                  visible: true
                }
              ]
            }
          ]
        }
      : []
    await route.fulfill({ json: { code: 200, data, msg: '' } })
  })
}

test('Skill 发布使用版本编号并在草稿更新时携带修订号', async ({ page }) => {
  await bootstrap(page)
  let status = 'DRAFT'
  const mutations: { path: string; query: string; body: unknown }[] = []
  await page.route('**/admin-api/ai/skill/**', async (route) => {
    const url = new URL(route.request().url())
    if (route.request().method() !== 'GET') {
      mutations.push({
        path: url.pathname,
        query: url.search,
        body: route.request().postDataJSON()
      })
      if (url.pathname.endsWith('/review')) status = 'REVIEWING'
      if (url.pathname.endsWith('/publish')) status = 'PUBLISHED'
    }
    const data = url.pathname.endsWith('/page')
      ? {
          list: [
            { id: 12, name: '选品', code: 'selection', currentVersion: 0, riskLevel: 'READ_ONLY' }
          ],
          total: 1
        }
      : url.pathname.endsWith('/version/list')
        ? [
            {
              id: 901,
              skillId: 12,
              version: 3,
              revision: 7,
              publishStatus: status,
              systemPrompt: '请输出选品依据',
              inputSchema: '{}',
              confirmationPolicy: 'NONE'
            }
          ]
        : url.pathname.endsWith('/test')
          ? { executionMode: 'READ_ONLY_EXECUTION', output: '只读选品测试输出' }
          : true
    await route.fulfill({ json: { code: 200, data, msg: '' } })
  })
  await page.goto('/ai/skill')
  await page.getByRole('button', { name: '版本与发布', exact: true }).click()
  const versions = page.getByRole('dialog').filter({ hasText: '选品 · 版本与发布' })
  await versions.getByRole('button', { name: '编辑', exact: true }).click()
  await page.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect
    .poll(() => mutations.find((item) => item.path.endsWith('/version/update'))?.body)
    .toMatchObject({ id: 901, expectedRevision: 7 })
  await versions.getByRole('button', { name: '提交审核', exact: true }).click()
  await versions.getByRole('button', { name: '发布版本', exact: true }).click()
  await page.getByRole('button', { name: '确定', exact: true }).click()
  await expect
    .poll(() => mutations.find((item) => item.path.endsWith('/version/publish'))?.query)
    .toBe('?id=901')
  await versions.getByRole('button', { name: '只读预览', exact: true }).click()
  await page.getByRole('button', { name: '生成只读预览', exact: true }).click()
  await expect
    .poll(() => mutations.filter((item) => item.path.endsWith('/test')).at(-1)?.body)
    .toMatchObject({ executeModel: false, skillVersionId: 901 })
  await page.getByPlaceholder('输入测试内容').fill('查询耳机选品')
  await page.getByText('执行只读模型测试', { exact: true }).click()
  await page.getByRole('button', { name: '执行只读测试', exact: true }).click()
  await expect
    .poll(() => mutations.filter((item) => item.path.endsWith('/test')).at(-1)?.body)
    .toMatchObject({ executeModel: true, skillVersionId: 901, input: '查询耳机选品' })
  await expect(page.getByTestId('skill-sandbox-output')).toHaveText('只读选品测试输出')
})

test('调用统计展示成功率与角色使用量，角色历史只读展示保存的版本', async ({ page }) => {
  await bootstrap(page)
  await page.route('**/admin-api/ai/mcp/**', async (route) => {
    const url = new URL(route.request().url())
    const data = url.pathname.endsWith('/stats')
      ? {
          total: 10,
          succeeded: 9,
          failed: 1,
          p95DurationMs: 280,
          denied: 2,
          roleUsage: [{ code: 'merchant', total: 7 }],
          skillUsage: [{ code: 'selection', total: 3 }]
        }
      : { list: [], total: 0 }
    await route.fulfill({ json: { code: 200, data, msg: '' } })
  })
  await page.goto('/ai/mcp')
  await page.getByRole('tab', { name: '调用日志', exact: true }).click()
  await expect(page.getByTestId('mcp-stats')).toContainText('90.0')
  await expect(page.getByTestId('mcp-stats')).toContainText('280')
  await page.getByText('角色与 Skill 使用量', { exact: true }).click()
  await expect(page.getByRole('cell', { name: 'merchant', exact: true })).toBeVisible()

  await page.route('**/admin-api/ai/chat-role/**', async (route) => {
    const url = new URL(route.request().url())
    const data = url.pathname.endsWith('/version/list')
      ? [
          {
            id: 1,
            roleId: 21,
            version: 4,
            publishStatus: 'PUBLISHED',
            definitionSnapshot: JSON.stringify({
              name: '历史商家角色',
              systemMessage: '旧版本投放建议'
            }),
            creator: '1',
            createTime: '2026-09-18 16:00:00'
          }
        ]
      : { list: [{ id: 21, name: '商家投放', status: 0, publicStatus: true }], total: 1 }
    await route.fulfill({ json: { code: 200, data, msg: '' } })
  })
  await page.goto('/capability-test/roles')
  await page.getByRole('button', { name: '版本历史', exact: true }).click()
  const history = page.getByRole('dialog').filter({ hasText: '商家投放 · 版本历史' })
  await expect(history.getByRole('cell', { name: 'PUBLISHED', exact: true })).toBeVisible()
  await history.locator('.el-table__expand-icon').click()
  await expect(history).toContainText('旧版本投放建议')
  await expect(history.getByRole('button', { name: '保存', exact: true })).toHaveCount(0)
})

test('MCP 连接失败保留失败提示，传输协议不包含任意进程执行', async ({ page }) => {
  await bootstrap(page)
  let createdServer: unknown
  await page.route('**/admin-api/ai/mcp/**', async (route) => {
    const url = new URL(route.request().url())
    let data: unknown = false
    if (url.pathname.endsWith('/server/page')) {
      data = {
        list: [{ id: 2, code: 'catalog', name: '商品服务', healthStatus: 'DOWN', status: 0 }],
        total: 1
      }
    }
    if (url.pathname.endsWith('/server/create')) {
      createdServer = route.request().postDataJSON()
      data = 88
    }
    await route.fulfill({ json: { code: 200, data, msg: '' } })
  })
  await page.goto('/ai/mcp')
  await page.getByRole('button', { name: '测试', exact: true }).click()
  await expect(page.getByText('连接失败，请检查服务配置和健康状态')).toBeVisible()
  await page.getByRole('button', { name: '注册服务', exact: true }).click()
  const dialog = page.getByRole('dialog').filter({ hasText: '注册 MCP 服务' })
  await dialog.getByText('SSE', { exact: true }).click()
  await expect(page.getByRole('option', { name: 'Streamable HTTP', exact: true })).toBeVisible()
  await expect(page.getByRole('option', { name: 'STDIO', exact: true })).toHaveCount(0)
  await page.keyboard.press('Escape')
  await dialog.getByRole('textbox', { name: '服务编码' }).fill('pricing')
  await dialog.getByRole('textbox', { name: '服务名称' }).fill('价格服务')
  await dialog.getByRole('textbox', { name: 'Endpoint' }).fill('https://mcp.example.com/sse')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect
    .poll(() => createdServer)
    .toMatchObject({
      code: 'pricing',
      name: '价格服务',
      location: 'REMOTE',
      transportProtocol: 'SSE',
      endpoint: 'https://mcp.example.com/sse',
      authType: 'NONE',
      status: 1
    })
})

test('新会话选择角色并从服务端确认卡片执行一次操作', async ({ page }) => {
  await bootstrap(page)
  let created = false
  let confirmations = 0
  let createBody: unknown
  const snapshot = JSON.stringify({
    schemaVersion: 2,
    roleCode: 'merchant',
    roleVersion: 3,
    riskLevel: 'ATTRIBUTION_WRITE',
    tools: [{ name: 'cps_generate_link', serverId: 2 }],
    skills: [{ code: 'merchant', version: 2 }]
  })
  const conversation = {
    id: '90071992547409930',
    title: '商家投放',
    roleId: 21,
    roleVersion: 3,
    modelName: '测试模型',
    createTime: Date.now(),
    effectiveCapabilitySnapshot: snapshot
  }
  await page.route('**/admin-api/ai/**', async (route) => {
    const url = new URL(route.request().url())
    let data: unknown = []
    if (url.pathname.endsWith('/published-list'))
      data = [
        {
          id: 21,
          name: '商家投放助手',
          description: '投放策略与转链',
          version: 3,
          riskLevel: 'ATTRIBUTION_WRITE'
        }
      ]
    if (url.pathname.endsWith('/create-my')) {
      created = true
      createBody = route.request().postDataJSON()
      data = conversation.id
    }
    if (url.pathname.endsWith('/my-list')) data = created ? [conversation] : []
    if (url.pathname.endsWith('/get-my')) data = conversation
    if (url.pathname.endsWith('/tool-confirmations'))
      data = confirmations
        ? []
        : [
            {
              id: '90071992547409931',
              toolLabel: '生成推广链接',
              summary: '商品编号：item-42',
              status: 'PENDING',
              expiresAt: '2099-01-01T00:00:00'
            }
          ]
    if (url.pathname.endsWith('/confirm-tool')) {
      confirmations++
      data = '推广链接已生成'
    }
    await route.fulfill({ json: { code: 200, data, msg: '' } })
  })
  await page.goto('/capability-test/chat')
  await page.getByTestId('chat-role-picker').click()
  await expect(page.getByTestId('chat-role-card')).toContainText('商家投放助手')
  await page.getByRole('button', { name: '使用此角色 · v3', exact: true }).click()
  await expect.poll(() => createBody).toEqual({ roleId: 21 })
  await expect(page.getByTestId('conversation-capabilities')).toContainText('merchant · v3')
  await expect(page.getByTestId('tool-confirmations')).toContainText('商品编号：item-42')
  expect(confirmations).toBe(0)
  await page.getByRole('button', { name: '确认执行一次', exact: true }).click()
  await expect.poll(() => confirmations).toBe(1)
  await expect(page.getByTestId('tool-confirmations')).toContainText('已处理：推广链接已生成')
  await expect(page.getByRole('button', { name: '确认执行一次', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '基于此会话新建其他角色会话', exact: true }).click()
  await page.getByRole('button', { name: '使用此角色 · v3', exact: true }).click()
  await expect.poll(() => createBody).toEqual({ roleId: 21, sourceConversationId: conversation.id })
  await page.route('**/admin-api/ai/chat/message/send-stream', async (route) => {
    const events = [
      {
        eventType: 'TOOL_CONFIRM_REQUIRED',
        toolExecution: {
          executionId: 'confirmation-2',
          intent: 'GENERATE_LINK',
          label: '生成推广链接',
          status: 'WAITING_CONFIRMATION',
          message: '等待用户确认'
        }
      },
      { eventType: 'TASK_COMPLETE', message: '响应已生成' }
    ]
    await route.fulfill({
      contentType: 'text/event-stream',
      body: events.map((data) => `data: ${JSON.stringify({ code: 0, data })}\n\n`).join('')
    })
  })
  await page.locator('textarea').fill('请为该商品生成推广链接')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByTestId('tool-executions')).toContainText('等待用户确认')
  await expect(page.getByTestId('tool-executions')).not.toContainText('已完成')
})

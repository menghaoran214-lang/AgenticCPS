import { expect, test } from '@playwright/test'
import {
  readCapabilitySummary,
  isConfirmationExpired
} from '../src/views/ai/chat/index/capabilities'

test('会话能力仅展示保存的版本且不显示内部工具名', () => {
  const result = readCapabilitySummary(
    JSON.stringify({
      schemaVersion: 2,
      roleCode: 'merchant',
      roleVersion: 3,
      riskLevel: 'ATTRIBUTION_WRITE',
      skills: [{ code: 'selection', version: 7 }],
      tools: [
        { name: 'cps_generate_link', serverId: 4 },
        { name: 'cps_search_goods', serverId: 4 }
      ]
    })
  )
  expect(result).toEqual({
    roleCode: 'merchant',
    roleVersion: 3,
    riskLevel: 'ATTRIBUTION_WRITE',
    skills: ['selection v7'],
    serviceCount: 1,
    toolCount: 2
  })
})

test('损坏或未知能力快照不能被当作已解析能力', () => {
  for (const value of ['{', '{}', '{"schemaVersion":99}', '{"schemaVersion":2,"tools":{}}']) {
    expect(readCapabilitySummary(value)).toBeNull()
  }
})

test('上海时间确认单过期后不允许继续确认', () => {
  const now = Date.parse('2026-09-18T08:00:00Z')
  expect(isConfirmationExpired('2026-09-18T15:59:59', now)).toBe(true)
  expect(isConfirmationExpired('2026-09-18T16:00:01', now)).toBe(false)
  expect(isConfirmationExpired('invalid', now)).toBe(true)
})

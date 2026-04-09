/**
 * smoke-test.js — k6 Smoke Test
 *
 * 目的：验证后端在最小负载下正常工作。
 * 运行：k6 run load-tests/smoke-test.js
 *
 * 检查清单：
 *   - [ ] POST /auth/device 返回 200 + token
 *   - [ ] GET /characters 返回 200 + 角色列表
 *   - [ ] GET /purchases 返回 200
 *   - [ ] GET /sync/:userId 返回 200
 *   - [ ] POST /purchase/verify 返回 200/404/409/422
 *   - [ ] POST /sync/:userId 返回 200/403
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 1,
  duration: '10s',
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.05'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:3000/api/v1';
const TEST_DEVICE_ID = `smoke-test-${Date.now()}`;

export default function () {
  // ── 1. 设备注册 ──────────────────────────────────────────────────
  const regRes = http.post(
    `${BASE_URL}/auth/device`,
    JSON.stringify({
      deviceId: TEST_DEVICE_ID,
      clientVersion: '1.0.0',
      platform: 'android',
    }),
    {
      headers: { 'Content-Type': 'application/json' },
    },
  );

  check(regRes, {
    'device registered (200)': (r) => r.status === 200,
    'response has token': (r) => !!r.json('data.token'),
    'response has userId': (r) => !!r.json('data.userId'),
  });

  const token = regRes.json('data.token');
  const userId = regRes.json('data.userId');

  if (!token) {
    // 如果注册失败，后续请求用假 token（模拟无认证场景）
    console.error('Device registration failed, aborting subsequent requests');
    return;
  }

  const authHeaders = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  };

  sleep(0.5);

  // ── 2. 获取角色列表 ──────────────────────────────────────────────
  const charsRes = http.get(`${BASE_URL}/characters`, {
    headers: authHeaders,
  });

  check(charsRes, {
    'characters loaded (200)': (r) => r.status === 200,
    'response has characters array': (r) => Array.isArray(r.json('data.characters')),
  });

  sleep(0.5);

  // ── 3. 获取用户购买记录 ──────────────────────────────────────────
  const purchasesRes = http.get(`${BASE_URL}/purchases`, {
    headers: authHeaders,
  });

  check(purchasesRes, {
    'purchases loaded (200)': (r) => r.status === 200,
    'response has purchases array': (r) => Array.isArray(r.json('data.purchases')),
  });

  sleep(0.5);

  // ── 4. 获取用户档案 ──────────────────────────────────────────────
  const profileRes = http.get(`${BASE_URL}/sync/${userId}`, {
    headers: authHeaders,
  });

  check(profileRes, {
    'profile loaded (200)': (r) => r.status === 200,
    'response has nickname': (r) => typeof r.json('data.nickname') === 'string',
  });

  sleep(0.5);

  // ── 5. 更新用户档案 ──────────────────────────────────────────────
  const updateRes = http.post(
    `${BASE_URL}/sync/${userId}`,
    JSON.stringify({
      nickname: `Smoke_${__VU}_${__ITER}`,
      profileJson: { likes: ['奶茶', '猫'] },
    }),
    { headers: authHeaders },
  );

  check(updateRes, {
    'profile updated (200)': (r) => r.status === 200,
    'response has updatedAt': (r) => typeof r.json('data.updatedAt') === 'number',
  });

  sleep(0.5);

  // ── 6. 尝试购买验证 ─────────────────────────────────────────────
  // 从角色列表中取一个角色来测试
  const chars = JSON.parse(charsRes.body);
  const firstChar = chars?.data?.characters?.[0];

  if (firstChar) {
    const verifyRes = http.post(
      `${BASE_URL}/purchase/verify`,
      JSON.stringify({
        characterId: firstChar.id,
        channel: 'alipay',
        channelOrderId: `smoke-order-${__VU}-${__ITER}-${Date.now()}`,
        paidAmount: firstChar.price,
        paidAt: Date.now(),
      }),
      { headers: authHeaders },
    );

    // 合法的成功或幂等（已购买）都算通过
    check(verifyRes, {
      'purchase verified (200|404|409|422)': (r) =>
        [200, 404, 409, 422].includes(r.status),
    });
  }

  sleep(1);
}

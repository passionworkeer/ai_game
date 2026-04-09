/**
 * load-test.js — k6 Load Test
 *
 * 目的：在逐步增长的并发下验证后端吞吐量和延迟稳定性。
 * 运行：k6 run load-tests/load-test.js
 *
 * 阶段：
 *   0–10s   预热：从 0 爬到 50 VU
 *   10–30s  峰值：稳定 100 VU
 *   30–40s  冷却：从 100 降到 0
 *
 * SLO：
 *   - p(95) 延迟 < 500ms
 *   - p(99) 延迟 < 1000ms
 *   - 错误率 < 1%
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '10s', target: 50 },
    { duration: '20s', target: 100 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:3000/api/v1';

export default function () {
  // 每个 VU 使用独立的 deviceId，避免 UUID 冲突
  const deviceId = `load-test-${__VU}-${Date.now()}`;

  // ── 1. 注册 ──────────────────────────────────────────────────────
  const regRes = http.post(
    `${BASE_URL}/auth/device`,
    JSON.stringify({
      deviceId,
      clientVersion: '1.0.0',
      platform: 'android',
    }),
    {
      headers: { 'Content-Type': 'application/json' },
    },
  );

  const ok = check(regRes, {
    'register (200)': (r) => r.status === 200,
  });

  if (!ok) {
    // 注册失败时跳过后续请求，防止无效 token 污染结果
    return;
  }

  const token = regRes.json('data.token');
  const userId = regRes.json('data.userId');

  if (!token) return;

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  };

  // ── 2. 获取角色列表 ──────────────────────────────────────────────
  check(
    http.get(`${BASE_URL}/characters`, { headers }),
    { 'GET /characters (200)': (r) => r.status === 200 },
  );

  // ── 3. 获取购买记录 ──────────────────────────────────────────────
  check(
    http.get(`${BASE_URL}/purchases`, { headers }),
    { 'GET /purchases (200)': (r) => r.status === 200 },
  );

  // ── 4. 获取档案 ──────────────────────────────────────────────────
  check(
    http.get(`${BASE_URL}/sync/${userId}`, { headers }),
    { 'GET /sync/:userId (200)': (r) => r.status === 200 },
  );

  // ── 5. 更新档案 ──────────────────────────────────────────────────
  check(
    http.post(
      `${BASE_URL}/sync/${userId}`,
      JSON.stringify({
        nickname: `LoadVU_${__VU}`,
        profileJson: { lastLoadTest: Date.now() },
      }),
      { headers },
    ),
    { 'POST /sync/:userId (200)': (r) => r.status === 200 },
  );

  // 每个虚拟用户完成后短暂休眠，模拟真实用户行为
  sleep(0.3);
}

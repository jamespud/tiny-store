/*
 * k6 load test script for order creation endpoint - DIRECT to order service
 *
 * Same workload as order_create.js but hits order:28080 directly
 * (bypasses gateway), path /order/trades instead of /api/order/trades.
 * Used to isolate whether the RPS ceiling is the gateway or the order service.
 *
 * Usage:
 *   BASE_URL=http://localhost:28080 SKU_ID=SKU-A-k6-1 k6 run order_create_direct.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// Custom metrics
const orderCreationErrors = new Counter('order_creation_errors');
const orderCreationSuccess = new Counter('order_creation_success');
const p95Latency = new Trend('order_create_p95_ms');
const p99Latency = new Trend('order_create_p99_ms');

// Environment configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:28080';
const VUS = __ENV.VUS || 100;
const DURATION = __ENV.DURATION || '60s';
const SKU_ID = __ENV.SKU_ID || 'SKU_A';

export const options = {
    vus: VUS,
    duration: DURATION,
    thresholds: {
        'http_req_duration{name:create_trade}': ['p(95)<2000'],
        'http_req_duration{name:create_trade}': ['p(99)<5000'],
        'http_req_failed{name:create_trade}': ['rate<0.05'],
        'http_reqs': ['rate>10'],
    },
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export default function () {
    const tradeId = `k6-direct-${Date.now()}-${randomString(8)}`;
    const idempotencyKey = `idem-${tradeId}`;
    const buyerId = `buyer-k6-${randomString(6)}`;

    const payload = JSON.stringify({
        tradeId: tradeId,
        buyerId: buyerId,
        buyerNick: 'k6-buyer',
        addressId: 'addr-k6-001',
        traceId: `trace-${tradeId}`,
        orderLines: [
            {
                skuId: SKU_ID,
                productId: 'prod-1',
                productName: 'Product A',
                shopId: 'SHOP_A',
                sellerId: 'seller-A',
                quantity: 1,
                priceCents: 1000,
                weightGrams: 0,
            },
        ],
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': idempotencyKey,
            'X-Trace-ID': `k6-trace-${tradeId}`,
        },
        tags: { name: 'create_trade' },
    };

    const response = http.post(`${BASE_URL}/order/trades`, payload, params);

    // Check response
    const success = check(response, {
        'status is 200': (r) => r.status === 200,
        'response has body': (r) => r.body.length > 0,
        'no 5xx errors': (r) => r.status < 500,
    });

    if (success) {
        orderCreationSuccess.add(1);
        p95Latency.add(response.timings.duration);
        p99Latency.add(response.timings.duration);
    } else {
        orderCreationErrors.add(1);
        console.error(`Failed request: status=${response.status}, body=${response.body}`);
    }

    // Try to parse response body
    try {
        const body = JSON.parse(response.body);
        check(body, {
            'response code is 0': (b) => b.code === 0,
            'has paymentIntentId': (b) => b.data && b.data.paymentIntentId,
        });
    } catch (e) {
        console.error(`Failed to parse response body: ${e}`);
    }

    // Think time (simulated user delay)
    // sleep(0.5);
}

export function handleSummary(data) {
    console.log('');
    console.log('===== Performance Summary (direct to order) =====');
    console.log(`Total Requests: ${data.metrics.http_reqs?.values?.count || 0}`);
    console.log(`Request Rate (RPS): ${data.metrics.http_reqs?.values?.rate?.toFixed(2) || 'N/A'}`);
    console.log(`Success: ${data.metrics.order_creation_success?.values?.count || 0}`);
    console.log(`Errors: ${data.metrics.order_creation_errors?.values?.count || 0}`);
    console.log(`Error Rate: ${((data.metrics.http_req_failed?.values?.rate || 0) * 100).toFixed(2)}%`);
    console.log('');
    console.log('===============================');

    return {
        'stdout': JSON.stringify(data, null, 2),
    };
}

/*
 * k6 load test script for order creation + payment (short-chain lifecycle)
 * 
 * Purpose: Test the complete create→pay flow under load
 * 
 * Metrics tracked:
 * - http_req_duration (p95, p99) for both create and pay
 * - end-to-end chain duration
 * - Success rate for complete lifecycle
 * 
 * Usage:
 *   k6 run order_create_and_pay.js
 *   k6 run --vus 20 --duration 30s order_create_and_pay.js
 *   BASE_URL=http://localhost:8080 k6 run order_create_and_pay.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// Custom metrics
const chainSuccess = new Counter('chain_success');
const chainFailure = new Counter('chain_failure');
const chainDuration = new Trend('chain_duration_ms');

// Environment configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = __ENV.VUS || 50;
const DURATION = __ENV.DURATION || '60s';

export const options = {
    vus: VUS,
    duration: DURATION,
    thresholds: {
        // End-to-end p95 should be below 5000ms
        'chain_duration_ms': ['p(95)<5000'],
        // Less than 10% chain failure rate
        'chain_failure': ['count<10'],
    },
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export default function () {
    const tradeId = `k6-chain-${Date.now()}-${randomString(8)}`;
    const idempotencyKey = `idem-${tradeId}`;
    const buyerId = `buyer-k6-${randomString(6)}`;
    const chainStart = Date.now();

    // Step 1: Create trade
    const createPayload = JSON.stringify({
        tradeId: tradeId,
        buyerId: buyerId,
        buyerNick: 'k6-buyer',
        addressId: 'addr-k6-001',
        traceId: `trace-${tradeId}`,
        orderLines: [
            {
                skuId: 'SKU_A',
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

    const createParams = {
        headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': idempotencyKey,
            'X-Trace-ID': `k6-trace-${tradeId}`,
        },
        tags: { name: 'create_trade' },
    };

    const createResponse = http.post(`${BASE_URL}/api/order/trades`, createPayload, createParams);
    
    const createSuccess = check(createResponse, {
        'create: status is 200': (r) => r.status === 200,
        'create: no 5xx': (r) => r.status < 500,
    });

    if (!createSuccess) {
        chainFailure.add(1);
        console.error(`Create failed: status=${createResponse.status}`);
        return;
    }

    let paymentIntentId;
    try {
        const createBody = JSON.parse(createResponse.body);
        if (createBody.code !== 0 || !createBody.data || !createBody.data.paymentIntentId) {
            chainFailure.add(1);
            console.error(`Create response invalid: ${createResponse.body}`);
            return;
        }
        paymentIntentId = createBody.data.paymentIntentId;
    } catch (e) {
        chainFailure.add(1);
        console.error(`Parse create response failed: ${e}`);
        return;
    }

    // Small delay to simulate user interaction
    // sleep(0.2);

    // Step 2: Pay trade
    const payPayload = JSON.stringify({
        paymentIntentId: paymentIntentId,
        payChannel: 'WECHAT_PAY',
        paymentOrderId: `pay-${tradeId}`,
    });

    const payParams = {
        headers: {
            'Content-Type': 'application/json',
            'X-Trace-ID': `k6-trace-${tradeId}`,
        },
        tags: { name: 'pay_trade' },
    };

    const payResponse = http.post(`${BASE_URL}/api/pay/payment-intents/${paymentIntentId}/pay`, payPayload, payParams);
    
    const paySuccess = check(payResponse, {
        'pay: status is 200': (r) => r.status === 200,
        'pay: no 5xx': (r) => r.status < 500,
    });

    if (!paySuccess) {
        chainFailure.add(1);
        console.error(`Pay failed: status=${payResponse.status}`);
        return;
    }

    try {
        const payBody = JSON.parse(payResponse.body);
        if (payBody.code !== 0) {
            chainFailure.add(1);
            console.error(`Pay response invalid: ${payResponse.body}`);
            return;
        }
    } catch (e) {
        chainFailure.add(1);
        console.error(`Parse pay response failed: ${e}`);
        return;
    }

    // Chain completed successfully
    const chainEnd = Date.now();
    const duration = chainEnd - chainStart;
    chainSuccess.add(1);
    chainDuration.add(duration);

    // Think time
    // sleep(1);
}

export function handleSummary(data) {
    console.log('');
    console.log('===== Lifecycle Chain Summary =====');
    console.log(`Total Chains Attempted: ${data.metrics.iterations?.values?.count || 0}`);
    console.log(`Success: ${data.metrics.chain_success?.values?.count || 0}`);
    console.log(`Failure: ${data.metrics.chain_failure?.values?.count || 0}`);
    
    if (data.metrics.chain_duration_ms?.values) {
        console.log('');
        console.log('End-to-End Chain Duration (ms):');
        console.log(`  p50 (median): ${data.metrics.chain_duration_ms.values['p(50)']?.toFixed(2) || 'N/A'}`);
        console.log(`  p90: ${data.metrics.chain_duration_ms.values['p(90)']?.toFixed(2) || 'N/A'}`);
        console.log(`  p95: ${data.metrics.chain_duration_ms.values['p(95)']?.toFixed(2) || 'N/A'}`);
        console.log(`  p99: ${data.metrics.chain_duration_ms.values['p(99)']?.toFixed(2) || 'N/A'}`);
        console.log(`  max: ${data.metrics.chain_duration_ms.values.max?.toFixed(2) || 'N/A'}`);
    }
    
    console.log('====================================');
    console.log('');
    console.log('NOTE: Stock capacity is 10000 units (SKU_A).');

    return {
        'stdout': JSON.stringify(data, null, 2),
    };
}

# Order Domain Validation and ACL Boundaries

## Overview

This document defines the clear boundaries between external validations (handled by ACL/Application layer) and aggregate invariants (enforced within the Order aggregate).

## Validation Layers

### 1. External Validations (ACL/Application Layer)

These validations involve external systems and should be completed **before** calling aggregate methods:

#### User Validations (`UserValidatorService`)
- **User existence and status**: Verify user exists and is active
- **User permissions**: Check if user can place orders (not blocked/suspended)
- **Address validation**: Verify shipping address belongs to user and is valid
- **Order limits**: Check daily/monthly order limits per user
- **Risk assessment**: Anti-fraud checks, unusual behavior detection

**Trusted Input**: After validation, aggregate receives `Buyer` object with verified `userId`, `buyerType`, and `level`.

#### Product Validations (`ProductValidatorService`)
- **Product availability**: Check if products are active and not delisted
- **Stock availability**: Verify sufficient inventory (snapshot at order time)
- **Price validity**: Confirm current prices match order request
- **Category restrictions**: Check if user can purchase certain categories
- **Regional availability**: Verify products can be sold to user's region

**Trusted Input**: After validation, aggregate receives `Product` objects with verified `productId`, current price, and availability status.

#### Coupon Validations (`CouponService`)
- **Coupon validity**: Check expiry dates, usage limits, and status
- **Eligibility**: Verify user can use these coupons
- **Applicability**: Confirm coupons apply to selected products
- **Conflict resolution**: Handle overlapping coupon rules
- **Usage tracking**: Reserve coupon usage quotas

**Trusted Input**: After validation, aggregate receives `Coupon` objects with verified conditions and pre-calculated `CouponAllocation`.

#### Pricing Validations (`OrderPriceCalculationService`)
- **Price calculation**: Calculate line totals, discounts, taxes, shipping
- **Promotion application**: Apply current promotions and campaigns
- **Currency conversion**: Handle multi-currency scenarios
- **Rounding policies**: Apply business rounding rules
- **Cross-validation**: Ensure calculated totals match business rules

**Trusted Input**: After validation, aggregate receives verified `PricingSummary` with correct totals and `DiscountAllocation`.

### 2. Aggregate Invariants (Order Domain)

These invariants are enforced within the aggregate and assume trusted input:

#### Financial Invariants
- **Amount conservation**: Total of line items equals pricing summary total (within rounding tolerance of 1 cent)
- **Non-negative amounts**: All monetary values must be >= 0
- **Allocation consistency**: Sum of allocations equals total discounts applied
- **Currency consistency**: All money objects use the same currency

#### State Transition Invariants
- **Valid transitions only**: State changes must follow the defined state machine rules
- **Terminal state protection**: No modifications allowed once order reaches terminal states
- **Prerequisites**: Required conditions before state transitions (e.g., payment before shipping)
- **Concurrent modification**: Version-based optimistic locking

#### Business Rule Invariants
- **Minimum order requirements**: At least one sub-order and one product line
- **Address requirement**: Valid shipping address for physical products
- **After-sale window**: Respect time windows for after-sale applications
- **Cancellation rules**: Honor cancellation policies based on order state

#### Data Integrity Invariants
- **Required fields**: Essential fields like `orderId`, `buyer`, `createdAt` must be present
- **Relationship consistency**: Sub-orders reference valid products in the order
- **Event ordering**: Domain events maintain causal ordering with version numbers

## Integration Patterns

### Order Creation Flow
```
1. Application Layer:
   - UserValidatorService.canUserPlaceOrder(userId, productIds)
   - ProductValidatorService.validateProducts(productIds)
   - CouponService.validateAndReserveCoupons(coupons)
   - OrderPriceCalculationService.calculatePricing(items, coupons)

2. Aggregate Layer:
   - Order.create(trustedArgs) // Only invariant checks
   - Validates amount conservation
   - Validates required fields
   - Generates domain events
```

### Payment Callback Flow
```
1. Application Layer:
   - IdempotencyService.tryAcquire(paymentCallbackId)
   - PaymentService.validateCallback(signature, amount)

2. Aggregate Layer:
   - order.onPaymentSuccess(trustedArgs)
   - Validates state transition rules
   - Updates payment status
   - Generates domain events
```

### State Query Flow
```
1. Application Layer:
   - Load order aggregate
   - StatusPriorityMatrix.derive(context)
   - Apply view-specific formatting

2. No aggregate business logic needed for read operations
```

## Error Handling Strategy

### External Validation Failures
- **Fail fast**: Return validation errors immediately to caller
- **Error codes**: Use specific error codes for each validation type
- **Logging**: Log validation failures for monitoring and debugging
- **Compensation**: Release any reserved resources (coupons, inventory)

### Invariant Violations
- **Domain exceptions**: Throw `OrderDomainException` with specific error codes
- **State consistency**: Ensure no partial state changes on failures
- **Event rollback**: Don't emit domain events if invariants fail
- **Alerting**: Critical invariant violations should trigger alerts

## Testing Strategy

### External Validation Tests
- **Mock external services**: Use test doubles for user/product/coupon services
- **Boundary value testing**: Test edge cases for limits and thresholds
- **Error simulation**: Test behavior when external services fail
- **Performance testing**: Ensure validations don't become bottlenecks

### Invariant Tests
- **Property-based testing**: Generate random valid/invalid inputs
- **State machine testing**: Verify all valid and invalid transitions
- **Concurrency testing**: Test optimistic locking under concurrent modifications
- **Data integrity**: Verify relationships and constraints are maintained

## Migration Considerations

When evolving validation rules:
1. **Backward compatibility**: New validations shouldn't break existing orders
2. **Graceful degradation**: Handle missing or invalid legacy data
3. **Feature flags**: Allow gradual rollout of new validation rules
4. **Data migration**: Update existing orders to meet new invariants where needed

## Monitoring and Observability

### Key Metrics
- **Validation failure rates**: Track failures by validation type
- **External service latency**: Monitor validation service response times
- **Invariant violation alerts**: Immediate notification for critical failures
- **Order creation success rate**: End-to-end success metrics

### Logging Standards
- **Structured logging**: Use consistent format for validation events
- **Correlation IDs**: Track requests across validation and domain layers
- **Sensitive data**: Mask PII in logs while preserving debugging info
- **Performance logging**: Track validation times for optimization
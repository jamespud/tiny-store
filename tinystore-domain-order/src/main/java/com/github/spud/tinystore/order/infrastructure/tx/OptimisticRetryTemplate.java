package com.github.spud.tinystore.order.infrastructure.tx;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * "乐观锁冲突只重试纯数据库状态迁移" 的模板（C2/C8）。
 *
 * <p>背景：同一个 trade / shop_order 行会被两条写入路径触碰——用户命令（取消、确认收货）
 * 与异步回执（促销提交回执、包裹签收）。多副本下它们在**不同 JVM**里各自加载、各自写，
 * 后写者因 {@code @Version} 失配而失败。
 *
 * <p>关键设计：
 * <ul>
 *   <li>每次尝试都在**独立的 REQUIRES_NEW 事务**里执行，因此重试时会重新加载最新版本，
 *       而不是复用同一个持久化上下文里的脏快照；</li>
 *   <li>只把**数据库状态迁移**交给本模板，外部调用（Feign 释放库存/优惠）留在外层，不会被重试；</li>
 *   <li>用 {@link TransactionTemplate} 而不是自调用方法，避免绕过 Spring 事务代理。</li>
 * </ul>
 */
@Slf4j
@Component
public class OptimisticRetryTemplate implements StateTransitionRetry {

    private final TransactionTemplate requiresNewTemplate;
    private final int maxAttempts;
    private final long backoffMillis;

    public OptimisticRetryTemplate(PlatformTransactionManager transactionManager,
            @Value("${order.tx.optimistic-retry.max-attempts:3}") int maxAttempts,
            @Value("${order.tx.optimistic-retry.backoff-ms:20}") long backoffMillis) {
        this.requiresNewTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffMillis = Math.max(0, backoffMillis);
    }

    /**
     * 执行一段纯数据库状态迁移，遇到乐观锁冲突时重新开事务重试。
     *
     * @param description 日志用描述（例如 {@code "close trade <tradeId>"}）
     * @param action 状态迁移动作，每次尝试都会在一个全新事务中重新执行
     */
    @Override
    public <T> T execute(String description, Supplier<T> action) {
        ObjectOptimisticLockingFailureException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return requiresNewTemplate.execute(status -> action.get());
            } catch (ObjectOptimisticLockingFailureException | CannotAcquireLockException failure) {
                lastFailure = failure instanceof ObjectOptimisticLockingFailureException optimistic
                        ? optimistic : null;
                log.warn("State transition conflict (attempt {}/{}): {}", attempt, maxAttempts,
                        description);
                if (attempt == maxAttempts) {
                    break;
                }
                sleepQuietly();
            }
        }
        log.error("State transition failed after {} attempts: {}", maxAttempts, description);
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new ObjectOptimisticLockingFailureException(
                "State transition conflicted after " + maxAttempts + " attempts: " + description, null);
    }

    private void sleepQuietly() {
        if (backoffMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}

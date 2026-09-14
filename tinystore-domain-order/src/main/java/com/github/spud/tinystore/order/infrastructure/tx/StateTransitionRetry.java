package com.github.spud.tinystore.order.infrastructure.tx;

import java.util.function.Supplier;

/**
 * 执行"纯数据库状态迁移"并处理乐观锁冲突的抽象（C2/C8）。
 *
 * <p>生产实现见 {@link OptimisticRetryTemplate}（每次尝试独立事务 + 冲突重试）。
 * 单测里可以注入直通实现（直接执行 action），无需真实事务管理器。
 */
public interface StateTransitionRetry {

    <T> T execute(String description, Supplier<T> action);
}

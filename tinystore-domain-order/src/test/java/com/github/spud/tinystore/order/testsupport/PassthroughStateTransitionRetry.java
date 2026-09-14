package com.github.spud.tinystore.order.testsupport;

import java.util.function.Supplier;

import com.github.spud.tinystore.order.infrastructure.tx.StateTransitionRetry;

/**
 * 单测用直通实现：不做事务包装、不做重试，直接执行动作。
 *
 * <p>用匿名/具名类而不是 lambda —— {@link StateTransitionRetry#execute} 是泛型方法，
 * Java 不允许泛型方法作为函数式接口描述符。
 */
public class PassthroughStateTransitionRetry implements StateTransitionRetry {

    @Override
    public <T> T execute(String description, Supplier<T> action) {
        return action.get();
    }
}

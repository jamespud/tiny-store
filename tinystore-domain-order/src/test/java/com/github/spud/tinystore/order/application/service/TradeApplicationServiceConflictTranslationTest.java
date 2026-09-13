package com.github.spud.tinystore.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.github.spud.tinystore.order.domain.exception.DomainConflictException;

/**
 * 覆盖 createTrade 失败路径上的异常翻译规则（C10）。
 *
 * <p>数据库唯一冲突反映的是调用方冲突（例如同一 tradeId 用不同幂等键重复提交），
 * 必须翻译成领域冲突让 HTTP 层返回 409，而不是 500；同时响应里不得出现
 * PostgreSQL 的约束名或 SQL 文本。
 */
@DisplayName("TradeApplicationService — 下单冲突翻译规则")
class TradeApplicationServiceConflictTranslationTest {

    private final TradeApplicationService service = new TradeApplicationService();

    @Test
    @DisplayName("唯一约束冲突翻译为 DomainConflictException 且不泄漏约束名")
    void dataIntegrityViolation_isTranslatedToDomainConflict() {
        DataIntegrityViolationException databaseError = new DataIntegrityViolationException(
                "could not execute statement; ERROR: duplicate key value violates unique constraint \"trade_trade_id_key\"");

        Exception translated = service.translateCreateConflict(databaseError, "trade-1");

        assertThat(translated).isInstanceOf(DomainConflictException.class);
        assertThat(translated.getMessage()).doesNotContain("trade_trade_id_key");
        assertThat(translated.getMessage()).doesNotContain("duplicate key");
        assertThat(translated.getMessage()).contains("trade-1");
        assertThat(translated).hasCause(databaseError);
    }

    @Test
    @DisplayName("被包装的唯一约束冲突同样会被翻译")
    void wrappedDataIntegrityViolation_isTranslatedToDomainConflict() {
        Exception wrapped = new IllegalStateException("persist failed",
                new DataIntegrityViolationException("violates unique constraint \"shop_order_order_id_key\""));

        Exception translated = service.translateCreateConflict(wrapped, "trade-2");

        assertThat(translated).isInstanceOf(DomainConflictException.class);
        assertThat(translated.getMessage()).doesNotContain("shop_order_order_id_key");
    }

    @Test
    @DisplayName("非数据库冲突保持原样，仍走 500 路径")
    void unrelatedFailure_passesThroughUnchanged() {
        RuntimeException boom = new RuntimeException("upstream unavailable");

        assertThat(service.translateCreateConflict(boom, "trade-3")).isSameAs(boom);
    }
}

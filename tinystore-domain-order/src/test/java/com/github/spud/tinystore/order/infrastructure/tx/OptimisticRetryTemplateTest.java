package com.github.spud.tinystore.order.infrastructure.tx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/**
 * C2：乐观锁冲突必须"在新事务里重新加载后重试"，且只重试纯数据库状态迁移。
 */
@DisplayName("OptimisticRetryTemplate — 乐观锁冲突重试")
class OptimisticRetryTemplateTest {

    private PlatformTransactionManager transactionManager;
    private OptimisticRetryTemplate template;

    @BeforeEach
    void setUp() {
        transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(status);
        template = new OptimisticRetryTemplate(transactionManager, 3, 0);
    }

    @Test
    @DisplayName("冲突后重试并最终成功")
    void retriesUntilSuccess() {
        AtomicInteger attempts = new AtomicInteger();

        String result = template.execute("close trade t-1", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new ObjectOptimisticLockingFailureException("Trade", "t-1");
            }
            return "closed";
        });

        assertThat(result).isEqualTo("closed");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("超过最大尝试次数后抛出最后一个冲突异常")
    void exhaustsAttempts() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> template.execute("close trade t-2", () -> {
            attempts.incrementAndGet();
            throw new ObjectOptimisticLockingFailureException("Trade", "t-2");
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("非乐观锁异常不重试，直接上抛")
    void otherExceptionsAreNotRetried() {
        assertThatThrownBy(() -> template.execute("close trade t-3", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        verify(transactionManager, times(1)).getTransaction(any());
    }
}

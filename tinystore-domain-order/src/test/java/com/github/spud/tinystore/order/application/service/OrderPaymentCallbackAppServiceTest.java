package com.github.spud.tinystore.order.application.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.spud.tinystore.order.application.service.OrderPaymentCallbackAppService.PaymentCallbackResult;
import com.github.spud.tinystore.order.application.service.OrderPaymentCallbackAppService.PaymentSuccessCallbackRequest;
import com.github.spud.tinystore.order.domain.statemachine.OrderStateMachineService;
import com.github.spud.tinystore.order.infrastructure.audit.OrderStatusAuditService;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.idempotency.OrderIdempotencyService;
import com.github.spud.tinystore.order.infrastructure.idempotency.OrderIdempotencyService.IdempotencyResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentCallbackAppServiceTest {

	@Mock
	private OrderIdempotencyService idempotencyService;
	@Mock
	private OrderStateMachineService stateMachineService;
	@Mock
	private OutboxEventService outboxEventService;
	@Mock
	private OrderStatusAuditService auditService;

	private OrderPaymentCallbackAppService appService;

	@BeforeEach
	void setUp() {
		appService = new OrderPaymentCallbackAppService(
			idempotencyService,
			stateMachineService,
			outboxEventService,
			auditService
		);
	}

	@Test
	void handlePaymentSuccess_shouldProcessAndCompleteIdempotency() {
		PaymentSuccessCallbackRequest request = new PaymentSuccessCallbackRequest();
		request.setRequestId("req-1");
		request.setOrderNo("ORDER-1");
		request.setPaymentTransactionId("PAY-1");
		request.setPaymentMethod("ALIPAY");
		request.setAmount(new BigDecimal("19.99"));

		when(idempotencyService.checkAndCreateIdempotency("req-1", "ORDER-1", "PAYMENT_SUCCESS_CALLBACK"))
			.thenReturn(Optional.empty());

		PaymentCallbackResult result = appService.handlePaymentSuccess(request);

		assertEquals("SUCCESS", result.getStatus());
		assertEquals("ORDER-1", result.getOrderNo());
		assertEquals("PAID", result.getMainStatus());

		verify(idempotencyService).checkAndCreateIdempotency("req-1", "ORDER-1", "PAYMENT_SUCCESS_CALLBACK");
		verify(outboxEventService).saveEvents(argThat(events -> !events.isEmpty()));
		verify(auditService).recordSystemStatusChange(
			eq("ORDER-1"),
			eq("PENDING_PAYMENT"),
			eq("PAID"),
			eq("payment-callback-service"),
			eq("支付成功回调处理"),
			any()
		);

		ArgumentCaptor<Object> responseCaptor = ArgumentCaptor.forClass(Object.class);
		verify(idempotencyService).completeIdempotency(eq("req-1"), responseCaptor.capture());
		Object captured = responseCaptor.getValue();
		assertSame(result, captured);

		verify(idempotencyService, never()).failIdempotency(anyString());
		verifyNoInteractions(stateMachineService);
	}

	@Test
	void handlePaymentSuccess_shouldReturnDuplicateWhenIdempotentRecordExists() {
		PaymentSuccessCallbackRequest request = new PaymentSuccessCallbackRequest();
		request.setRequestId("req-dup");
		request.setOrderNo("ORDER-dup");

		IdempotencyResult duplicateResult = new IdempotencyResult(true, "ORDER-dup", "cached-response");
		when(idempotencyService.checkAndCreateIdempotency("req-dup", "ORDER-dup", "PAYMENT_SUCCESS_CALLBACK"))
			.thenReturn(Optional.of(duplicateResult));

		PaymentCallbackResult result = appService.handlePaymentSuccess(request);

		assertEquals("DUPLICATE", result.getStatus());
		assertEquals("ORDER-dup", result.getOrderNo());
		assertEquals("cached-response", result.getData());

		verify(idempotencyService).checkAndCreateIdempotency("req-dup", "ORDER-dup", "PAYMENT_SUCCESS_CALLBACK");
		verify(idempotencyService, never()).completeIdempotency(anyString(), any());
		verify(idempotencyService, never()).failIdempotency(anyString());

		verifyNoInteractions(outboxEventService);
		verifyNoInteractions(auditService);
		verifyNoInteractions(stateMachineService);
	}
}

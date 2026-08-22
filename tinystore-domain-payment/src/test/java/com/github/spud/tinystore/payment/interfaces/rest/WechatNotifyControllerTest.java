package com.github.spud.tinystore.payment.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.spud.tinystore.infrastructure.rpc.order.OrderClient;
import com.github.spud.tinystore.infrastructure.rpc.order.dto.response.OrderRpcResponse;
import com.github.spud.tinystore.payment.application.pay.WechatV3CallbackProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A4 微信通知入口：验签通过→解密→通知订单域→200；验签失败→4XX + FAIL。
 */
@WebMvcTest(controllers = WechatNotifyController.class,
	excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class})
@DisplayName("WechatNotifyController — wechat v3 notify endpoint (A4)")
class WechatNotifyControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private WechatV3CallbackProcessor processor;

	@MockitoBean
	private OrderClient orderClient;

	private static final String BODY = "{\"event_type\":\"TRANSACTION.SUCCESS\","
		+ "\"resource\":{\"algorithm\":\"AEAD_AES_256_GCM\",\"ciphertext\":\"YWJj\","
		+ "\"associated_data\":\"\",\"nonce\":\"TinystoreNce12\",\"original_type\":\"transaction\"}}";
	private static final String TXN = "{\"out_trade_no\":\"TS202608231234567890\","
		+ "\"trade_state\":\"SUCCESS\",\"amount\":{\"total\":1000,\"currency\":\"CNY\"}}";

	@Test
	@DisplayName("valid signed callback notifies order domain and returns 200")
	void validSignedCallback_notifiesOrder_returns200() throws Exception {
		when(processor.verifySignature(anyString(), anyString(), anyString(), eq(BODY), any(), any()))
			.thenReturn(true);
		when(processor.decryptResource(anyString(), anyString(), anyString(), anyString())).thenReturn(TXN);
		when(orderClient.paymentCallback(anyString(), anyString(), any())).thenReturn(new OrderRpcResponse<>());

		mockMvc.perform(post("/api/pay/wechat/notify")
				.header("Wechatpay-Serial", "4B1B261F3D30D4903721FA7A2395F815")
				.header("Wechatpay-Timestamp", "1787532000")
				.header("Wechatpay-Nonce", "c0kZP6cSbveFpn0U")
				.header("Wechatpay-Signature", "sig")
				.contentType("application/json")
				.content(BODY))
			.andExpect(status().isOk());

		verify(orderClient).paymentCallback(eq("TS202608231234567890"), anyString(), any());
	}

	@Test
	@DisplayName("tampered signature returns 400 FAIL")
	void invalidSignature_returns400Fail() throws Exception {
		when(processor.verifySignature(anyString(), anyString(), anyString(), eq(BODY), any(), any()))
			.thenReturn(false);

		mockMvc.perform(post("/api/pay/wechat/notify")
				.header("Wechatpay-Serial", "4B1B261F3D30D4903721FA7A2395F815")
				.header("Wechatpay-Timestamp", "1787532000")
				.header("Wechatpay-Nonce", "c0kZP6cSbveFpn0U")
				.header("Wechatpay-Signature", "sig")
				.contentType("application/json")
				.content(BODY))
			.andExpect(status().isBadRequest())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("FAIL")));
	}
}

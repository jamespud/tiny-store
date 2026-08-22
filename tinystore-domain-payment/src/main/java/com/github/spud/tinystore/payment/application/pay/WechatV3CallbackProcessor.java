package com.github.spud.tinystore.payment.application.pay;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 微信支付 v3 回调处理：RSA-SHA256 验签 + AES-256-GCM 解密 resource（A4 真实渠道回调）。
 *
 * <p>纯 JDK 实现，无三方依赖。验签用微信支付平台证书（按 Wechatpay-Serial 选择），
 * 解密用商户 APIv3 密钥。接入真实渠道时填入商户平台证书与 APIv3 密钥即可。
 */
@Component
public class WechatV3CallbackProcessor {

	/**
	 * 校验回调签名。message = timestamp\nnonce\nbody\n，使用微信支付平台证书公钥做 RSA-SHA256 (PKCS1v15)。
	 *
	 * @param platformCertPem 平台证书 PEM（非空时优先使用）
	 * @param platformPublicKeyPem 平台公钥 PEM（cert 为空时使用）
	 * @return 验签是否通过
	 */
	public boolean verifySignature(String timestamp, String nonce, String signature, String body,
		String platformCertPem, String platformPublicKeyPem) throws Exception {
		PublicKey key = loadPublicKey(platformCertPem, platformPublicKeyPem);
		String message = timestamp + "\n" + nonce + "\n" + body + "\n";
		Signature verifier = Signature.getInstance("SHA256withRSA");
		verifier.initVerify(key);
		verifier.update(message.getBytes(StandardCharsets.UTF_8));
		return verifier.verify(Base64.getDecoder().decode(signature));
	}

	/**
	 * 解密 resource.ciphertext（AEAD_AES_256_GCM），返回业务明文 JSON。
	 */
	public String decryptResource(String ciphertext, String nonce, String aad, String apiV3Key)
		throws Exception {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec spec = new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8));
		cipher.init(Cipher.DECRYPT_MODE,
			new SecretKeySpec(apiV3Key.getBytes(StandardCharsets.UTF_8), "AES"), spec);
		if (aad != null && !aad.isEmpty()) {
			cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
		}
		byte[] plain = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
		return new String(plain, StandardCharsets.UTF_8);
	}

	private PublicKey loadPublicKey(String certPem, String publicKeyPem) throws Exception {
		if (certPem != null && !certPem.isBlank()) {
			String der = certPem
				.replace("-----BEGIN CERTIFICATE-----", "")
				.replace("-----END CERTIFICATE-----", "")
				.replaceAll("\\s", "");
			return CertificateFactory.getInstance("X.509")
				.generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(der)))
				.getPublicKey();
		}
		String der = publicKeyPem
			.replace("-----BEGIN PUBLIC KEY-----", "")
			.replace("-----END PUBLIC KEY-----", "")
			.replaceAll("\\s", "");
		return KeyFactory.getInstance("RSA")
			.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(der)));
	}
}

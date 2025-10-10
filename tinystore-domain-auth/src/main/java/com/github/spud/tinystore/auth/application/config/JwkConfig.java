package com.github.spud.tinystore.auth.application.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class JwkConfig {

	@Value("${tinystore.auth.keystore.location:classpath:keystore/tinystore-auth.jks}")
	private org.springframework.core.io.Resource keystore;

	@Value("${tinystore.auth.keystore.password:changeit}")
	private String keystorePassword;

	@Value("${tinystore.auth.key.alias:auth}")
	private String keyAlias;

	@Value("${tinystore.auth.key.password:changeit}")
	private String keyPassword;

	@Bean
	public JWKSource<SecurityContext> jwkSource() {
		try {
			KeyStore ks = KeyStore.getInstance("JKS");
			try (InputStream is = keystore.getInputStream()) {
				ks.load(is, keystorePassword.toCharArray());
			}
			var cert = ks.getCertificate(keyAlias);
			RSAPublicKey publicKey = (RSAPublicKey) cert.getPublicKey();
			PrivateKey pk = (PrivateKey) ks.getKey(keyAlias, keyPassword.toCharArray());
			RSAPrivateKey privateKey = (RSAPrivateKey) pk;
			RSAKey rsaKey = new RSAKey.Builder(publicKey)
				.privateKey(privateKey)
				.keyID(keyAlias)
				.build();
			JWKSet jwkSet = new JWKSet(rsaKey);
			return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load JWK from keystore", e);
		}
	}
}

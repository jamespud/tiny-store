package com.github.spud.tinystore.order.infrastructure.acl;

import org.springframework.stereotype.Component;

/**
 * 默认无操作实现：总是返回通过，便于灰度启用真实验签实现。
 */
@Component
public class NoopSignatureVerifier implements SignatureVerifier {
  @Override
  public boolean verify(String signature, String timestamp, Object payload) {
    return true;
  }
}

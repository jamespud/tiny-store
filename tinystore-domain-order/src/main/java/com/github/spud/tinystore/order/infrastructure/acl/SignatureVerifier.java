package com.github.spud.tinystore.order.infrastructure.acl;

/**
 * 签名校验接口，可由实际网关/平台签名算法替换实现。
 */
public interface SignatureVerifier {

  /**
   * 校验签名是否有效。
   *
   * @param signature   请求头 X-Signature
   * @param timestamp   请求头 X-Timestamp（字符串形式）
   * @param payload     参与签名的负载（可为 DTO 或原始 JSON 文本）
   * @return true 表示验签通过
   */
  boolean verify(String signature, String timestamp, Object payload);
}

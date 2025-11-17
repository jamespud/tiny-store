package com.github.spud.tinystore.order.interfaces.error;

/**
 * 代表未认证/验签失败等 401 错误。
 */
public class UnauthorizedException extends RuntimeException {
  private final String errorCode;

  public UnauthorizedException(String message) {
    super(message);
    this.errorCode = "ORDER-UNAUTHORIZED";
  }

  public UnauthorizedException(String message, String errorCode) {
    super(message);
    this.errorCode = errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}

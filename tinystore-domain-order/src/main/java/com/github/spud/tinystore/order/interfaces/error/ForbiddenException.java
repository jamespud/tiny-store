package com.github.spud.tinystore.order.interfaces.error;

public class ForbiddenException extends RuntimeException {
  private final String errorCode;

  public ForbiddenException(String message) {
    super(message);
    this.errorCode = OrderErrorCodes.FORBIDDEN;
  }

  public ForbiddenException(String message, String errorCode) {
    super(message);
    this.errorCode = (errorCode == null || errorCode.isBlank()) ? OrderErrorCodes.FORBIDDEN : errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}

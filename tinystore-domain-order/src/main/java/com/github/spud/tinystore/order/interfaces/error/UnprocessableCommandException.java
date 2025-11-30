package com.github.spud.tinystore.order.interfaces.error;

public class UnprocessableCommandException extends RuntimeException {
  private final String errorCode;

  public UnprocessableCommandException(String message) {
    super(message);
    this.errorCode = OrderErrorCodes.UNPROCESSABLE;
  }

  public UnprocessableCommandException(String message, String errorCode) {
    super(message);
    this.errorCode = (errorCode == null || errorCode.isBlank()) ? OrderErrorCodes.UNPROCESSABLE : errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}

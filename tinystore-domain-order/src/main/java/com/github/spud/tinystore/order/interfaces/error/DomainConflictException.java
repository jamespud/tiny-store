package com.github.spud.tinystore.order.interfaces.error;

public class DomainConflictException extends RuntimeException {
  private final String errorCode;

  public DomainConflictException(String message) {
    super(message);
    this.errorCode = OrderErrorCodes.CONFLICT;
  }

  public DomainConflictException(String message, String errorCode) {
    super(message);
    this.errorCode = (errorCode == null || errorCode.isBlank()) ? OrderErrorCodes.CONFLICT : errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}

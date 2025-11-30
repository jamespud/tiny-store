package com.github.spud.tinystore.order.interfaces.error;

public class ResourceNotFoundException extends RuntimeException {
  private final String errorCode;

  public ResourceNotFoundException(String message) {
    super(message);
    this.errorCode = OrderErrorCodes.NOT_FOUND;
  }

  public ResourceNotFoundException(String message, String errorCode) {
    super(message);
    this.errorCode = (errorCode == null || errorCode.isBlank()) ? OrderErrorCodes.NOT_FOUND : errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}

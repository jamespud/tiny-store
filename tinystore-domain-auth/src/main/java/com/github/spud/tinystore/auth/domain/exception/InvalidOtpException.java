package com.github.spud.tinystore.auth.domain.exception;

public class InvalidOtpException extends RuntimeException {

  public InvalidOtpException(String message) {
    super(message);
  }
}

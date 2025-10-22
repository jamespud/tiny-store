package com.github.spud.tinystore.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * @param principal  The principal (e.g., username, email, phone number) used for authentication.
 * @param credential The credential (e.g., password or OTP) used for authentication.
 * @param authType   The type of authentication being performed (e.g., "password", "otp").
 */
public record LoginRequest(@NotNull String principal, @NotNull String credential,
                           @NotNull AuthType authType) {

  public enum AuthType {
    PASSWORD(0),
    OTP(1);

    final int code;

    AuthType(int code) {
      this.code = code;
    }

    static AuthType of(int code) {
      for (AuthType type : AuthType.values()) {
        if (type.code == code) {
          return type;
        }
      }
      throw new IllegalArgumentException("Invalid AuthType code: " + code);
    }
  }

}

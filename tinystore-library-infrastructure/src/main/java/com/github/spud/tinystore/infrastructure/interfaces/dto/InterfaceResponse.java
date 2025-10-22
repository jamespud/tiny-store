package com.github.spud.tinystore.infrastructure.interfaces.dto;

public record InterfaceResponse<T>(int code, String message, T data) {

	public static <T> InterfaceResponse<T> success(T data) {
		return new InterfaceResponse<T>(200, "Success", data);
	}

	public static <T> InterfaceResponse<T> fail(int code, String message) {
		return new InterfaceResponse<T>(code, message, null);
	}
}

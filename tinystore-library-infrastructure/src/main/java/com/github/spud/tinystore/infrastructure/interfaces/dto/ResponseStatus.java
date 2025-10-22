package com.github.spud.tinystore.infrastructure.interfaces.dto;

import lombok.Getter;

@Getter
public enum ResponseStatus {

	SUCCESS(200, "Success"),
	ERROR(500, "Error");

	ResponseStatus(int code, String message) {
	}

}

package com.github.spud.tinystore.infrastrucutre.vo;

import lombok.Data;

/**
 * @author Spud
 * @date 2025/8/26
 */
@Data
public class CommonResponse<T> {
	private int code;
	private String message;
	private T data;
	
	public static <T> CommonResponse<T> success(T data) {
		CommonResponse<T> response = new CommonResponse<>();
		response.setCode(200);
		response.setMessage("Success");
		response.setData(data);
		return response;
	}
	
	public static <T> CommonResponse<T> error(int code, String message) {
		CommonResponse<T> response = new CommonResponse<>();
		response.setCode(code);
		response.setMessage(message);
		response.setData(null);
		return response;
	}
	
}

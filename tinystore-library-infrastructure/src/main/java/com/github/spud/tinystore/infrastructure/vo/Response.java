package com.github.spud.tinystore.infrastructure.vo;

import lombok.Data;

/**
 * 通用响应包装
 */
public class Response<T> {

	private String code;
	private String message;
	private T data;
	private String traceId; // 新增

	public static <T> Response<T> ok(T data) {
		Response<T> r = new Response<>();
		r.code = "OK";
		r.message = "SUCCESS";
		r.data = data;
		return r;
	}

	public static <T> Response<T> ok(T data, String traceId) {
		Response<T> r = ok(data);
		r.traceId = traceId;
		return r;
	}

	public static <T> Response<T> error(String code, String msg) {
		Response<T> r = new Response<>();
		r.code = code;
		r.message = msg;
		return r;
	}

	public static <T> Response<T> error(String code, String msg, String traceId) {
		Response<T> r = error(code, msg);
		r.traceId = traceId;
		return r;
	}

	public String getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}

	public T getData() {
		return data;
	}

	public String getTraceId() {
		return traceId;
	}

	public void setTraceId(String traceId) {
		this.traceId = traceId;
	}
}

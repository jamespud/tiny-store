package com.github.spud.tinystore.promotion.interfaces.dto;

public class ChangeReason {

	private String code;
	private String detail;

	public static ChangeReason of(String code, String detail) {
		ChangeReason r = new ChangeReason();
		r.setCode(code);
		r.setDetail(detail);
		return r;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getDetail() {
		return detail;
	}

	public void setDetail(String detail) {
		this.detail = detail;
	}
}


package com.github.spud.tinystore.auth.interfaces.rest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

	@GetMapping("/login")
	public String login(Model model) {
		if (!model.containsAttribute("otpForm")) {
			model.addAttribute("otpForm", new OtpForm());
		}
		return "login";
	}

	public static class OtpForm {
		private String phone;
		private String code;

		public String getPhone() {
			return phone;
		}

		public void setPhone(String phone) {
			this.phone = phone;
		}

		public String getCode() {
			return code;
		}

		public void setCode(String code) {
			this.code = code;
		}
	}
}

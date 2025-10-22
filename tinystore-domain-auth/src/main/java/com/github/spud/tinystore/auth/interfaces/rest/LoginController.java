package com.github.spud.tinystore.auth.interfaces.rest;

import com.github.spud.tinystore.auth.interfaces.dto.request.LoginRequest;
import com.github.spud.tinystore.auth.interfaces.dto.response.LoginResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LoginController {

  @GetMapping("/login")
  public String login(Model model) {
    if (!model.containsAttribute("otpForm")) {
      model.addAttribute("otpForm", new OtpForm());
    }
    return "login";
  }

  @PostMapping
  @ResponseBody
  public LoginResponse usernamePasswordLogin(
      @RequestBody LoginRequest request) {
    throw new UnsupportedOperationException("Not implemented");
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

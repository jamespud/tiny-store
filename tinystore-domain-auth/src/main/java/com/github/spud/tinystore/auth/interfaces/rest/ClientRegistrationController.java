package com.github.spud.tinystore.auth.interfaces.rest;

import com.github.spud.tinystore.auth.application.dto.RegisteredClientDto;
import com.github.spud.tinystore.auth.application.dto.RegisteredClientRequest;
import com.github.spud.tinystore.auth.application.service.ClientRegistrationApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oidc")
public class ClientRegistrationController {

  private final ClientRegistrationApplicationService registrationService;

  public ClientRegistrationController(ClientRegistrationApplicationService registrationService) {
    this.registrationService = registrationService;
  }

  /**
   * Dynamic client registration endpoint. Accepts token via header "X-Registration-Token" or
   * request param "registration_token".
   */
  @PostMapping("/register")
  public ResponseEntity<RegisteredClientDto> register(
    @Valid @RequestBody RegisteredClientRequest request,
    @RequestHeader(value = "X-Registration-Token", required = false) String headerToken,
    @RequestParam(value = "registration_token", required = false) String paramToken) {
    String token = StringUtils.hasText(headerToken) ? headerToken : paramToken;
    RegisteredClientDto result = registrationService.register(request, token);
    return ResponseEntity.ok(result);
  }
}
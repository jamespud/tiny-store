package com.github.spud.tinystore.auth.interfaces.rest;

import com.github.spud.tinystore.auth.application.dto.RegisteredClientDto;
import com.github.spud.tinystore.auth.application.dto.RegisteredClientRequest;
import com.github.spud.tinystore.auth.application.service.ClientRegistrationApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oidc")
public class ClientRegistrationController {

  private final ClientRegistrationApplicationService service;

  public ClientRegistrationController(ClientRegistrationApplicationService service) {
    this.service = service;
  }

  @PostMapping("/register")
  public ResponseEntity<RegisteredClientDto> register(
      @Valid @RequestBody RegisteredClientRequest request,
      @RequestHeader(value = "X-Registration-Token", required = false) String registrationToken) {
    RegisteredClientDto dto = service.register(request, registrationToken);
    return ResponseEntity.ok(dto);
  }

  @GetMapping("/register/{clientId}")
  public ResponseEntity<RegisteredClientDto> get(@PathVariable("clientId") String clientId,
      @RequestHeader(value = "X-Registration-Token", required = false) String registrationToken) {
    // Could validate token or admin privilege here if needed
    RegisteredClientDto dto = service.get(clientId);
    return ResponseEntity.ok(dto);
  }
}
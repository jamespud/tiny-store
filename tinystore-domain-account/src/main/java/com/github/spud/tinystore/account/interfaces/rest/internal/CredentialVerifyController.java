package com.github.spud.tinystore.account.interfaces.rest.internal;

import com.github.spud.tinystore.account.application.CredentialVerifyApplicationService;
import com.github.spud.tinystore.account.interfaces.dto.internal.CredentialVerifyRequest;
import com.github.spud.tinystore.account.interfaces.dto.internal.CredentialVerifyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/account/credentials")
@RequiredArgsConstructor
public class CredentialVerifyController {

    private final CredentialVerifyApplicationService credentialVerifyApplicationService;

    @PostMapping("/verify")
    public ResponseEntity<CredentialVerifyResponse> verify(@RequestBody CredentialVerifyRequest request) {
        CredentialVerifyResponse result = credentialVerifyApplicationService.verify(request.phone(), request.password());
        return ResponseEntity.ok(result);
    }
}


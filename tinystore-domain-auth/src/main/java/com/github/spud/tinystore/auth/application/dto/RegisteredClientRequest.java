package com.github.spud.tinystore.auth.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RegisteredClientRequest(
    @NotBlank String clientName,
    @NotEmpty List<String> redirectUris,
    @NotEmpty List<String> grantTypes,
    @NotNull String tokenEndpointAuthMethod,
    List<String> scopes,
    String jwks,
    String jwksUri
) {}
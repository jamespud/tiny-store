package com.github.spud.tinystore.auth.application.dto;

import java.util.List;

public record RegisteredClientDto(
    String clientId,
    String clientName,
    List<String> redirectUris,
    List<String> grantTypes,
    List<String> scopes,
    String tokenEndpointAuthMethod,
    String clientSecret
) {}
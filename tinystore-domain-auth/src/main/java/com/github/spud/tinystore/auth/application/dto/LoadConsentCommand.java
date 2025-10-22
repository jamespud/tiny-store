package com.github.spud.tinystore.auth.application.dto;

import java.util.List;

public record LoadConsentCommand(String clientId,
                                 String state,
                                 List<String> requestedScopes,
                                 String username) {

}
package com.github.spud.tinystore.auth.application.dto;

import java.util.Set;

public record ConsentView(String clientId,
                          String state,
                          String username,
                          Set<String> scopesToApprove,
                          Set<String> previouslyApprovedScopes) {

}
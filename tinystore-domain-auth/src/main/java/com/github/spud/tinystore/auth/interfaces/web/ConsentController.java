package com.github.spud.tinystore.auth.interfaces.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Controller
public class ConsentController {

    @GetMapping("/oauth2/consent")
    public String consent(
            @RequestParam("client_id") String clientId,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "scope", required = false) List<String> scopes,
            @AuthenticationPrincipal(expression = "username") String username,
            Model model) {

        Set<String> scopesToApprove = new LinkedHashSet<>();
        if (scopes != null) {
            scopesToApprove.addAll(scopes);
        }
        // 简化：previouslyApprovedScopes 暂为空集，若需可查询 consent 表
        Set<String> previouslyApprovedScopes = Set.of();

        model.addAttribute("clientId", clientId);
        model.addAttribute("state", state);
        model.addAttribute("username", username);
        model.addAttribute("scopesToApprove", scopesToApprove);
        model.addAttribute("previouslyApprovedScopes", previouslyApprovedScopes);
        return "consent";
    }
}

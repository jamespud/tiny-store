package com.github.spud.tinystore.auth.application.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tinystore.auth.dynamic-registration")
public class DynamicRegistrationProperties {

  /** 是否启用动态客户端注册 */
  private boolean enabled = true;

  /** 是否要求提供注册令牌 */
  private boolean requireToken = true;

  /** 允许的注册令牌列表（可选），为空时仅检查非空 */
  private List<String> allowedTokens = List.of();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isRequireToken() {
    return requireToken;
  }

  public void setRequireToken(boolean requireToken) {
    this.requireToken = requireToken;
  }

  public List<String> getAllowedTokens() {
    return allowedTokens;
  }

  public void setAllowedTokens(List<String> allowedTokens) {
    this.allowedTokens = allowedTokens;
  }
}
package com.github.spud.tinystore.interfaces.config;

import com.github.spud.tinystore.interfaces.aspect.LogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebLoConfig implements WebMvcConfigurer {

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // 注册日志拦截器，匹配所有接口（/**），排除静态资源（可选）
    registry.addInterceptor(new LogInterceptor())
      .addPathPatterns("/**")
      .excludePathPatterns("/static/**", "/favicon.ico", "/error");
  }
}
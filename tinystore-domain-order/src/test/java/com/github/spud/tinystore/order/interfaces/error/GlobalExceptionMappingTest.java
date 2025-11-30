package com.github.spud.tinystore.order.interfaces.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class GlobalExceptionMappingTest {

  @RestController
  static class DummyController {
    @GetMapping(path = "/error/403", produces = MediaType.APPLICATION_JSON_VALUE)
    public String f403() { throw new ForbiddenException("forbidden", "ORDER-4030"); }
    @GetMapping(path = "/error/409", produces = MediaType.APPLICATION_JSON_VALUE)
    public String f409() { throw new DomainConflictException("conflict", "ORDER-4090"); }
    @GetMapping(path = "/error/422", produces = MediaType.APPLICATION_JSON_VALUE)
    public String f422() { throw new UnprocessableCommandException("unprocessable", "ORDER-4220"); }
  }

  private MockMvc mvc() {
    return MockMvcBuilders.standaloneSetup(new DummyController())
        .setControllerAdvice(new GlobalExceptionHandler(new org.springframework.beans.factory.ObjectProvider<>() {
          @Override public com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics getObject(Object... args) { return null; }
          @Override public com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics getIfAvailable() { return null; }
          @Override public com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics getIfAvailable(java.util.function.Supplier<com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics> supplier) { return null; }
          @Override public com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics getObject() { return null; }
        }))
        .build();
  }

  @Test
  void maps_403_with_errorCode() throws Exception {
    mvc().perform(get("/error/403"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ORDER-4030"))
        .andExpect(jsonPath("$.message").value("forbidden"));
  }

  @Test
  void maps_409_with_errorCode() throws Exception {
    mvc().perform(get("/error/409"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ORDER-4090"))
        .andExpect(jsonPath("$.message").value("conflict"));
  }

  @Test
  void maps_422_with_errorCode() throws Exception {
    mvc().perform(get("/error/422"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errorCode").value("ORDER-4220"))
        .andExpect(jsonPath("$.message").value("unprocessable"));
  }
}

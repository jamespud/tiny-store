package com.github.spud.tinystore.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.github.spud.tinystore")
@EnableDiscoveryClient
@EnableFeignClients
public class TinystoreAuthApplication {

  public static void main(String[] args) {
    SpringApplication.run(TinystoreAuthApplication.class, args);
  }
}

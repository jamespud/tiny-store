package com.github.spud.tinystore.promotion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class PromotionApplication {

	public static void main(String[] args) {
		SpringApplication.run(PromotionApplication.class, args);
	}
}

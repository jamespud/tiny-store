package com.github.spud.tinystore.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * @author Spud
 * @date 2025/8/12
 */
@EnableFeignClients
@SpringBootApplication(scanBasePackages = "com.github.spud.tinystore")
public class OrderApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderApplication.class, args);
	}
}

package com.github.spud.tinystore.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Spud
 * @date 2025/8/10
 */
@SpringBootApplication(scanBasePackages = "com.github.spud.tinystore")
public class WarehouseApplication {

	public static void main(String[] args) {
		SpringApplication.run(WarehouseApplication.class, args);
	}
}

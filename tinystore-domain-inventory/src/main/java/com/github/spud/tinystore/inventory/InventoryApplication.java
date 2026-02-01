package com.github.spud.tinystore.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author Spud
 * @date 2025/8/10
 */
@SpringBootApplication(scanBasePackages = {
		"com.github.spud.tinystore.inventory",
		"com.github.spud.tinystore.infrastructure"
})
@EnableScheduling
public class InventoryApplication {
	public static void main(String[] args) {
		SpringApplication.run(InventoryApplication.class, args);
	}
}

package com.github.spud.tinystore.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Spud
 * @date 2025/8/15
 */
@SpringBootApplication(scanBasePackages = "com.github.spud.tinystore")
public class PaymentApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentApplication.class, args);
	}
}

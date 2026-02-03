package com.github.spud.tinystore.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Order Service Application
 */
@SpringBootApplication
@EnableFeignClients(basePackages = {
		"com.github.spud.tinystore.order.infrastructure.acl",
		"com.github.spud.tinystore.infrastructure.rpc"
})
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}

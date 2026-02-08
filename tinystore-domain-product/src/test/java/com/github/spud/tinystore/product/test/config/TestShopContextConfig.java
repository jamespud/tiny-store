package com.github.spud.tinystore.product.test.config;

import com.github.spud.tinystore.product.infrastructure.persistence.jpa.config.ShopRepositoryConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration for ShopContext
 * Provides a fixed shop ID for all tests to avoid dependency on HTTP headers
 */
@TestConfiguration
public class TestShopContextConfig {

    /**
     * Override the default ShopContext with a test version that returns a fixed shop ID
     * Using singleton scope and pre-set shop ID
     */
    @Bean
    @Primary
    public ShopRepositoryConfig.ShopContext testShopContext() {
        ShopRepositoryConfig.ShopContext context = new ShopRepositoryConfig.ShopContext();
        context.setShopId("SHOP_TEST");  // Fixed shop ID for all tests
        return context;
    }
}

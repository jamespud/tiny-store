package com.github.spud.tinystore.product.domain.rules;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * RuleRegistry - Registry for pricing rule types and their constructors
 * 
 * Responsibilities:
 * - Register rule types with their construction logic
 * - Provide factory methods for rule instantiation from JSONB
 * - Support polymorphic rule deserialization
 * 
 * Rule types to register:
 * - PercentOffRule: Percentage discount
 * - FlatOffRule: Fixed amount discount
 * - TimeWindowPercentOffRule: Time-bounded percentage discount
 * - TagFlatOffRule: Tag-based flat discount
 * - BuyXGetYRule: Promotional bundle rule
 * 
 * Usage:
 * 1. Register rule types at startup
 * 2. PricingRuleMapper uses registry to construct domain rules from entities
 * 3. SimpleRuleEngine uses registry for rule interpretation
 */
@Component
public class RuleRegistry {
    
    private final Map<String, Function<String, PricingRule>> ruleFactories = new HashMap<>();
    
    /**
     * Register a rule type with its factory function
     * 
     * @param ruleType Rule type identifier (e.g., "PERCENT_OFF")
     * @param factory Function that constructs rule from JSONB content
     */
    public void register(String ruleType, Function<String, PricingRule> factory) {
        ruleFactories.put(ruleType, factory);
    }
    
    /**
     * Construct a pricing rule from type and JSONB content
     * 
     * @param ruleType Rule type identifier
     * @param jsonContent JSONB content string
     * @return Constructed pricing rule
     * @throws IllegalArgumentException if rule type not registered
     */
    public PricingRule construct(String ruleType, String jsonContent) {
        Function<String, PricingRule> factory = ruleFactories.get(ruleType);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown rule type: " + ruleType);
        }
        return factory.apply(jsonContent);
    }
    
    /**
     * Check if rule type is registered
     * 
     * @param ruleType Rule type identifier
     * @return true if registered
     */
    public boolean isRegistered(String ruleType) {
        return ruleFactories.containsKey(ruleType);
    }
    
    // TODO: Add registration for built-in rule types in @PostConstruct
    // Example:
    // @PostConstruct
    // public void registerBuiltInRules() {
    //     register("PERCENT_OFF", json -> deserializePercentOffRule(json));
    //     register("FLAT_OFF", json -> deserializeFlatOffRule(json));
    //     ...
    // }
}

package com.github.spud.tinystore.product.domain.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * RuleConflictChecker - Static validation for pricing rule conflicts
 * <p>
 * Detects conflicts before rule activation: 1. Priority conflicts: Rules with same priority in same
 * scope 2. Exclusive group conflicts: Multiple rules in same exclusive group overlap in time 3.
 * Time window overlaps: Rules with incompatible conditions active simultaneously 4. Scope
 * ambiguity: Over-specific rules that shadow broader rules incorrectly
 * <p>
 * Used during: - Rule creation/update in admin interface - Rule activation workflow - Bulk rule
 * import validation
 * <p>
 * Does NOT handle runtime rule selection - that's SimpleRuleEngine's job
 */
@Component
public class RuleConflictChecker {

	/**
	 * Validate a new/updated rule against existing active rules
	 *
	 * @param newRule       Rule to validate
	 * @param existingRules Currently active rules in same scope
	 * @return List of detected conflicts (empty if valid)
	 */
	public List<RuleConflict> checkConflicts(PricingRule newRule, List<PricingRule> existingRules) {
		List<RuleConflict> conflicts = new ArrayList<>();

		// Check priority conflicts
		conflicts.addAll(checkPriorityConflicts(newRule, existingRules));

		// Check exclusive group conflicts
		conflicts.addAll(checkExclusiveGroupConflicts(newRule, existingRules));

		// Check time window overlaps
		conflicts.addAll(checkTimeWindowConflicts(newRule, existingRules));

		return conflicts;
	}

	/**
	 * Check for rules with same priority in overlapping scopes
	 *
	 * @param newRule       Rule to check
	 * @param existingRules Existing rules
	 * @return List of priority conflicts
	 */
	private List<RuleConflict> checkPriorityConflicts(PricingRule newRule,
		List<PricingRule> existingRules) {
		// TODO: Implement priority conflict detection
		// - Same priority + overlapping product/category scope = conflict
		// - Consider time window intersection
		return Collections.emptyList();
	}

	/**
	 * Check for exclusive group violations Rules in same exclusive group should not have overlapping
	 * time windows
	 *
	 * @param newRule       Rule to check
	 * @param existingRules Existing rules
	 * @return List of exclusive group conflicts
	 */
	private List<RuleConflict> checkExclusiveGroupConflicts(PricingRule newRule,
		List<PricingRule> existingRules) {
		// TODO: Implement exclusive group conflict detection
		// - If newRule has exclusiveGroup, find other rules in same group
		// - Check time window overlap
		// - Report conflict if overlap found
		return Collections.emptyList();
	}

	/**
	 * Check for problematic time window overlaps
	 *
	 * @param newRule       Rule to check
	 * @param existingRules Existing rules
	 * @return List of time window conflicts
	 */
	private List<RuleConflict> checkTimeWindowConflicts(PricingRule newRule,
		List<PricingRule> existingRules) {
		// TODO: Implement time window overlap detection
		// - Check if [effectiveTime, expireTime] intervals overlap
		// - Consider rule priority and exclusive groups
		// - Report conflicts that would cause ambiguous rule selection
		return Collections.emptyList();
	}

	/**
	 * RuleConflict - Describes a detected conflict between rules
	 */
	public static class RuleConflict {

		private final String type;
		private final String message;
		private final String ruleCode1;
		private final String ruleCode2;

		public RuleConflict(String type, String message, String ruleCode1, String ruleCode2) {
			this.type = type;
			this.message = message;
			this.ruleCode1 = ruleCode1;
			this.ruleCode2 = ruleCode2;
		}

		public String getType() {
			return type;
		}

		public String getMessage() {
			return message;
		}

		public String getRuleCode1() {
			return ruleCode1;
		}

		public String getRuleCode2() {
			return ruleCode2;
		}

		@Override
		public String toString() {
			return String.format("%s: %s (rules: %s vs %s)", type, message, ruleCode1, ruleCode2);
		}
	}
}

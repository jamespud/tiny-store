package com.github.spud.tinystore.order.application.command.user;

import lombok.Builder;
import lombok.Getter;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Getter
@Builder
public class SubmitOrderCommand extends ConfirmOrderCommand {

	private final String idempotentKey;
}

package com.github.spud.tinystore.order.application.command.user;

import com.github.spud.tinystore.order.application.command.user.PreviewOrderCommand.MerchantSkuDTO.SkuItemDTO;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Getter
@Builder
public class SubmitOrderCommand extends PreviewOrderCommand {

	private final String idempotentKey;
}

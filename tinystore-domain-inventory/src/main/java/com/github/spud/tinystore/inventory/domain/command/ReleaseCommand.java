package com.github.spud.tinystore.inventory.domain.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReleaseCommand {

	@NotBlank
	String reservationId;
	String reason;
}


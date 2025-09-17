package com.github.spud.tinystore.inventory.domain.command;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Value;

@Value
public class BatchAvailableQuery {

	@NotEmpty
	List<Item> items;

	@Value
	public static class Item {

		String shopId;
		String skuId;
	}
}


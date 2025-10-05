package com.github.spud.tinystore.product.domain.model.valueobject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkuAttributePack {

	private List<SkuAttribute> attributes;

	// Constructor from Map for DTO conversion
	public SkuAttributePack(Map<String, Object> attributeMap) {
		this.attributes = new ArrayList<>();
		if (attributeMap != null) {
			attributeMap.forEach((key, value) -> {
				SkuAttribute attr = new SkuAttribute(
					key,
					value != null ? value.toString() : ""
				);
				this.attributes.add(attr);
			});
		}
	}

	// Convert to Map for DTO response
	public Map<String, Object> toMap() {
		Map<String, Object> map = new HashMap<>();
		if (attributes != null) {
			attributes.forEach(attr -> map.put(attr.getKey(), attr.getValue()));
		}
		return map;
	}
}

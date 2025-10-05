package com.github.spud.tinystore.product.domain.model.valueobject;

import java.util.ArrayList;
import java.util.List;

/**
 * 属性模板 - 定义类目的必选和可选属性
 */
public class AttributeTemplate {

	private List<AttributeDefinition> mandatoryAttributes = new ArrayList<>();
	private List<AttributeDefinition> optionalAttributes = new ArrayList<>();

	public void addMandatory(AttributeDefinition attribute) {
		if (attribute != null && !mandatoryAttributes.contains(attribute)) {
			mandatoryAttributes.add(attribute);
		}
	}

	public void addOptional(AttributeDefinition attribute) {
		if (attribute != null && !optionalAttributes.contains(attribute)) {
			optionalAttributes.add(attribute);
		}
	}

	public List<AttributeDefinition> getMandatoryAttributes() {
		return new ArrayList<>(mandatoryAttributes);
	}

	public List<AttributeDefinition> getOptionalAttributes() {
		return new ArrayList<>(optionalAttributes);
	}
}

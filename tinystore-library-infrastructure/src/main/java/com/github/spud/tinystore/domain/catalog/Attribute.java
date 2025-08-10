package com.github.spud.tinystore.domain.catalog;

import com.github.spud.tinystore.domain.BaseEntity;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "attributes", schema = "catalog")
public class Attribute extends BaseEntity {
    
    @Column(name = "name", nullable = false, unique = true)
    private String name;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "attr_type", nullable = false)
    private AttributeType attrType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "value_mode", nullable = false)
    private ValueMode valueMode;
    
    @Column(name = "unit")
    private String unit;
    
    @OneToMany(mappedBy = "attribute", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AttributeOption> attributeOptions = new ArrayList<>();
    
    @OneToMany(mappedBy = "attribute", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SkuAttributeValue> skuAttributeValues = new ArrayList<>();
    
    public enum AttributeType {
        BASIC, SALE
    }
    
    public enum ValueMode {
        TEXT, NUMBER, SELECT, MULTI_SELECT
    }
    
    // Getters and Setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public AttributeType getAttrType() {
        return attrType;
    }
    
    public void setAttrType(AttributeType attrType) {
        this.attrType = attrType;
    }
    
    public ValueMode getValueMode() {
        return valueMode;
    }
    
    public void setValueMode(ValueMode valueMode) {
        this.valueMode = valueMode;
    }
    
    public String getUnit() {
        return unit;
    }
    
    public void setUnit(String unit) {
        this.unit = unit;
    }
    
    public List<AttributeOption> getAttributeOptions() {
        return attributeOptions;
    }
    
    public void setAttributeOptions(List<AttributeOption> attributeOptions) {
        this.attributeOptions = attributeOptions;
    }
    
    public List<SkuAttributeValue> getSkuAttributeValues() {
        return skuAttributeValues;
    }
    
    public void setSkuAttributeValues(List<SkuAttributeValue> skuAttributeValues) {
        this.skuAttributeValues = skuAttributeValues;
    }
}

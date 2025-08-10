package com.github.spud.tinystore.domain.catalog;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "sku_attribute_values", schema = "catalog")
public class SkuAttributeValue {
    
    @EmbeddedId
    private SkuAttributeValueId id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("skuId")
    @JoinColumn(name = "sku_id")
    private ProductSku sku;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("attributeId")
    @JoinColumn(name = "attribute_id")
    private Attribute attribute;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_option_id")
    private AttributeOption attributeOption;
    
    @Column(name = "value_text")
    private String valueText;
    
    // Getters and Setters
    public SkuAttributeValueId getId() {
        return id;
    }
    
    public void setId(SkuAttributeValueId id) {
        this.id = id;
    }
    
    public ProductSku getSku() {
        return sku;
    }
    
    public void setSku(ProductSku sku) {
        this.sku = sku;
    }
    
    public Attribute getAttribute() {
        return attribute;
    }
    
    public void setAttribute(Attribute attribute) {
        this.attribute = attribute;
    }
    
    public AttributeOption getAttributeOption() {
        return attributeOption;
    }
    
    public void setAttributeOption(AttributeOption attributeOption) {
        this.attributeOption = attributeOption;
    }
    
    public String getValueText() {
        return valueText;
    }
    
    public void setValueText(String valueText) {
        this.valueText = valueText;
    }
    
    @Embeddable
    public static class SkuAttributeValueId {
        @Column(name = "sku_id")
        private UUID skuId;
        
        @Column(name = "attribute_id")
        private UUID attributeId;
        
        public SkuAttributeValueId() {}
        
        public SkuAttributeValueId(UUID skuId, UUID attributeId) {
            this.skuId = skuId;
            this.attributeId = attributeId;
        }
        
        // Getters and Setters
        public UUID getSkuId() {
            return skuId;
        }
        
        public void setSkuId(UUID skuId) {
            this.skuId = skuId;
        }
        
        public UUID getAttributeId() {
            return attributeId;
        }
        
        public void setAttributeId(UUID attributeId) {
            this.attributeId = attributeId;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            
            SkuAttributeValueId that = (SkuAttributeValueId) o;
            
            if (skuId != null ? !skuId.equals(that.skuId) : that.skuId != null) return false;
            return attributeId != null ? attributeId.equals(that.attributeId) : that.attributeId != null;
        }
        
        @Override
        public int hashCode() {
            int result = skuId != null ? skuId.hashCode() : 0;
            result = 31 * result + (attributeId != null ? attributeId.hashCode() : 0);
            return result;
        }
    }
}

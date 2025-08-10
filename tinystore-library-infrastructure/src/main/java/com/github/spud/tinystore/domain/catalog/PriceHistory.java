package com.github.spud.tinystore.domain.catalog;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "price_history", schema = "catalog")
public class PriceHistory {
    
    @EmbeddedId
    private PriceHistoryId id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("skuId")
    @JoinColumn(name = "sku_id")
    private ProductSku sku;
    
    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom = LocalDateTime.now();
    
    @Column(name = "price_original", nullable = false)
    private Long priceOriginal;
    
    @Column(name = "price_sale", nullable = false)
    private Long priceSale;
    
    // Getters and Setters
    public PriceHistoryId getId() {
        return id;
    }
    
    public void setId(PriceHistoryId id) {
        this.id = id;
    }
    
    public ProductSku getSku() {
        return sku;
    }
    
    public void setSku(ProductSku sku) {
        this.sku = sku;
    }
    
    public LocalDateTime getValidFrom() {
        return validFrom;
    }
    
    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }
    
    public Long getPriceOriginal() {
        return priceOriginal;
    }
    
    public void setPriceOriginal(Long priceOriginal) {
        this.priceOriginal = priceOriginal;
    }
    
    public Long getPriceSale() {
        return priceSale;
    }
    
    public void setPriceSale(Long priceSale) {
        this.priceSale = priceSale;
    }
    
    @Embeddable
    public static class PriceHistoryId {
        @Column(name = "sku_id")
        private UUID skuId;
        
        @Column(name = "valid_from")
        private LocalDateTime validFrom;
        
        public PriceHistoryId() {}
        
        public PriceHistoryId(UUID skuId, LocalDateTime validFrom) {
            this.skuId = skuId;
            this.validFrom = validFrom;
        }
        
        // Getters and Setters
        public UUID getSkuId() {
            return skuId;
        }
        
        public void setSkuId(UUID skuId) {
            this.skuId = skuId;
        }
        
        public LocalDateTime getValidFrom() {
            return validFrom;
        }
        
        public void setValidFrom(LocalDateTime validFrom) {
            this.validFrom = validFrom;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            
            PriceHistoryId that = (PriceHistoryId) o;
            
            if (skuId != null ? !skuId.equals(that.skuId) : that.skuId != null) return false;
            return validFrom != null ? validFrom.equals(that.validFrom) : that.validFrom != null;
        }
        
        @Override
        public int hashCode() {
            int result = skuId != null ? skuId.hashCode() : 0;
            result = 31 * result + (validFrom != null ? validFrom.hashCode() : 0);
            return result;
        }
    }
}

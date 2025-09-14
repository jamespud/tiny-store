package com.github.spud.tinystore.cart.interfaces.dto;

public class CartLineDTO {
    private String shopId;
    private String spuId;
    private String skuId;
    private Integer quantity;
    private Boolean selected;
    private Boolean valid;

    public CartLineDTO(String shopId, String spuId, String skuId, Integer quantity, Boolean selected, Boolean valid) {
        this.shopId = shopId;
        this.spuId = spuId;
        this.skuId = skuId;
        this.quantity = quantity;
        this.selected = selected;
        this.valid = valid;
    }

    public String getShopId() { return shopId; }
    public String getSpuId() { return spuId; }
    public String getSkuId() { return skuId; }
    public Integer getQuantity() { return quantity; }
    public Boolean getSelected() { return selected; }
    public Boolean getValid() { return valid; }
}


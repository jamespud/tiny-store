package com.github.spud.tinystore.domain.catalog;

import com.github.spud.tinystore.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product_skus", schema = "catalog")
public class ProductSku extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(name = "sku_code", unique = true)
	private String skuCode;

	@Column(name = "title", nullable = false)
	private String title;

	@Column(name = "price_original", nullable = false)
	private Long priceOriginal;

	@Column(name = "price_sale", nullable = false)
	private Long priceSale;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "CNY";

	@Column(name = "weight_grams")
	private Integer weightGrams;

	@Column(name = "volume_cm3")
	private Integer volumeCm3;

	@Column(name = "barcode")
	private String barcode;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private SkuStatus status = SkuStatus.ACTIVE;

	@Column(name = "attributes_hash")
	private String attributesHash;

	@OneToMany(mappedBy = "sku", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<SkuAttributeValue> skuAttributeValues = new ArrayList<>();

	@OneToMany(mappedBy = "sku", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<PriceHistory> priceHistories = new ArrayList<>();

	public enum SkuStatus {
		ACTIVE, INACTIVE, DELETED
	}

	// Getters and Setters
	public Product getProduct() {
		return product;
	}

	public void setProduct(Product product) {
		this.product = product;
	}

	public String getSkuCode() {
		return skuCode;
	}

	public void setSkuCode(String skuCode) {
		this.skuCode = skuCode;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
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

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public Integer getWeightGrams() {
		return weightGrams;
	}

	public void setWeightGrams(Integer weightGrams) {
		this.weightGrams = weightGrams;
	}

	public Integer getVolumeCm3() {
		return volumeCm3;
	}

	public void setVolumeCm3(Integer volumeCm3) {
		this.volumeCm3 = volumeCm3;
	}

	public String getBarcode() {
		return barcode;
	}

	public void setBarcode(String barcode) {
		this.barcode = barcode;
	}

	public SkuStatus getStatus() {
		return status;
	}

	public void setStatus(SkuStatus status) {
		this.status = status;
	}

	public String getAttributesHash() {
		return attributesHash;
	}

	public void setAttributesHash(String attributesHash) {
		this.attributesHash = attributesHash;
	}

	public List<SkuAttributeValue> getSkuAttributeValues() {
		return skuAttributeValues;
	}

	public void setSkuAttributeValues(List<SkuAttributeValue> skuAttributeValues) {
		this.skuAttributeValues = skuAttributeValues;
	}

	public List<PriceHistory> getPriceHistories() {
		return priceHistories;
	}

	public void setPriceHistories(List<PriceHistory> priceHistories) {
		this.priceHistories = priceHistories;
	}
}

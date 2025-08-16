package com.github.spud.tinystore.product.domain;

import com.github.spud.tinystore.infrastrucutre.domain.inventory.Stock;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/8/10
 */
@Slf4j
@Service
public class StockService {

	@Autowired
	private StockRepository repository;

	/**
	 * 根据sku查询所有库存数量
	 */
	public Integer getBySkuId(UUID SkuId) {
		return repository.findAllBySkuId(SkuId);
	}

	public Stock getById(UUID id) {
		return repository.findById(id).orElseThrow(() -> new EntityNotFoundException(id.toString()));
	}

	public void freezeStock(UUID id, Integer amount) {
		Stock stock = repository.findById(id)
			.orElseThrow(() -> new EntityNotFoundException(id.toString()));
		stock.freeze(amount);
		repository.save(stock);
		log.debug("库存冻结，商品：{}，数量：{}，现有库存：{}，现存冻结：{}", id, amount,
			stock.getTotal(), stock.getReserved());
	}

	public void thawStock(UUID id, Integer amount) {
		Stock stock = repository.findById(id)
			.orElseThrow(() -> new EntityNotFoundException(id.toString()));
		stock.thaw(amount);
		repository.save(stock);
		log.debug("库存解冻，商品：{}，数量：{}，现有库存：{}，现存冻结：{}", id, amount,
			stock.getTotal(), stock.getReserved());
	}

	public void deductStock(UUID id, Integer amount) {
		Stock stock = repository.findById(id)
			.orElseThrow(() -> new EntityNotFoundException(id.toString()));
		stock.deductFrozenStock(amount);
		repository.save(stock);
		log.debug("库存扣减，商品：{}，数量：{}，现有库存：{}，现存冻结：{}", id, amount,
			stock.getTotal(), stock.getReserved());
	}

	public void increaseStock(UUID id, Integer amount) {
		Stock stock = repository.findById(id)
			.orElseThrow(() -> new EntityNotFoundException(id.toString()));
		stock.increase(amount);
		repository.save(stock);
		log.debug("库存增加，商品：{}，数量：{}，现有库存：{}，现存冻结：{}", id, amount,
			stock.getTotal(), stock.getReserved());
	}

	public void decreaseStock(UUID id, Integer amount) {
		Stock stock = repository.findById(id)
			.orElseThrow(() -> new EntityNotFoundException(id.toString()));
		stock.decrease(amount);
		repository.save(stock);
		log.debug("库存减少，商品：{}，数量：{}，现有库存：{}，现存冻结：{}", id, amount,
			stock.getTotal(), stock.getReserved());
	}
}

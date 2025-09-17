package com.github.spud.tinystore.inventory.interfaces.rest;

import com.github.spud.tinystore.infrastructure.vo.Response;
import com.github.spud.tinystore.inventory.domain.command.AdjustTotalCommand;
import com.github.spud.tinystore.inventory.domain.command.BatchAvailableQuery;
import com.github.spud.tinystore.inventory.domain.command.ConfirmCommand;
import com.github.spud.tinystore.inventory.domain.command.ReleaseCommand;
import com.github.spud.tinystore.inventory.domain.command.ReserveCommand;
import com.github.spud.tinystore.inventory.domain.model.Reservation;
import com.github.spud.tinystore.inventory.domain.service.InventoryDomainService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@Validated
public class InventoryController {

	private final InventoryDomainService service;

	public InventoryController(InventoryDomainService service) {
		this.service = service;
	}

	@PostMapping("/reservations")
	public Response<Reservation> reserve(@RequestBody @Valid ReserveReq req) {
		Reservation r = service.reserve(ReserveCommand.builder()
			.shopId(req.shopId).skuId(req.skuId).quantity(req.quantity).expireSeconds(req.expireSeconds)
			.operationId(req.operationId).build());
		return Response.ok(r);
	}

	@PostMapping("/reservations/{id}/confirm")
	public Response<Void> confirm(@PathVariable("id") String id) {
		service.confirm(ConfirmCommand.builder().reservationId(id).build());
		return Response.ok(null);
	}

	@PostMapping("/reservations/{id}/release")
	public Response<Void> release(@PathVariable("id") String id, @RequestBody ReleaseReq req) {
		service.release(ReleaseCommand.builder().reservationId(id).reason(req.reason).build());
		return Response.ok(null);
	}

	@PostMapping("/stock/adjust")
	public Response<Void> adjust(@RequestBody @Valid AdjustReq req) {
		service.adjustTotal(
			AdjustTotalCommand.builder().shopId(req.shopId).skuId(req.skuId).delta(req.delta)
				.reason(req.reason).build());
		return Response.ok(null);
	}

	@GetMapping("/stock/available")
	public Response<Long> available(@RequestParam @NotBlank String shopId,
		@RequestParam @NotBlank String skuId) {
		return Response.ok(service.available(shopId, skuId));
	}

	@PostMapping("/stock/available/batch")
	public Response<Map<String, Long>> batch(@RequestBody @Valid BatchReq req) {
		BatchAvailableQuery query = new BatchAvailableQuery(
			req.items.stream().map(i -> new BatchAvailableQuery.Item(i.shopId, i.skuId)).toList());
		return Response.ok(service.batchAvailable(query));
	}

	public record ReserveReq(@NotBlank String shopId, @NotBlank String skuId, @Positive long quantity,
	                         @Positive int expireSeconds, String operationId) {

	}

	public record ReleaseReq(String reason) {

	}

	public record AdjustReq(@NotBlank String shopId, @NotBlank String skuId, long delta,
	                        String reason) {

	}

	public record BatchReq(@NotEmpty List<Item> items) {

		public record Item(@NotBlank String shopId, @NotBlank String skuId) {

		}
	}
}


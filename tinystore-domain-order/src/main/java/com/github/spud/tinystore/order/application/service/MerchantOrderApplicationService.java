package com.github.spud.tinystore.order.application.service;

import com.github.spud.tinystore.order.application.command.merchant.AcceptOrderCommand;
import com.github.spud.tinystore.order.application.command.merchant.ApproveCancelOrderCommand;
import com.github.spud.tinystore.order.application.command.merchant.RejectCancelOrderCommand;
import com.github.spud.tinystore.order.application.command.merchant.RejectOrderCommand;
import org.springframework.stereotype.Service;

@Service
public class MerchantOrderApplicationService {

	public Object receiveOrder(AcceptOrderCommand cmd) {
		return null;
	}

	public Object rejectOrder(RejectOrderCommand cmd) {
		return null;
	}

	public Object approveCancelOrder(ApproveCancelOrderCommand cmd) {
		return null;
	}

	public Object rejectCancelOrder(RejectCancelOrderCommand cmd) {
		return null;
	}

	public Object shipOrder(Object order) {
		return null;
	}

	public Object deliverOrder(Object order) {
		return null;
	}

	public Object aftersaleOrder(Object order) {
		return null;
	}
}

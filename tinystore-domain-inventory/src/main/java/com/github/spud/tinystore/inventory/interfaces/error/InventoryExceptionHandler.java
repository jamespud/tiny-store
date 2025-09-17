package com.github.spud.tinystore.inventory.interfaces.error;

import com.github.spud.tinystore.infrastructure.vo.Response;
import com.github.spud.tinystore.inventory.domain.error.BusinessException;
import com.github.spud.tinystore.inventory.domain.error.InventoryErrorCode;
import com.github.spud.tinystore.inventory.domain.exception.InventoryBusinessException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理（占位）
 */
@Slf4j
@RestControllerAdvice
public class InventoryExceptionHandler {

	@ExceptionHandler(InventoryBusinessException.class)
	public Response<Void> handleBiz(InventoryBusinessException e) {
		return Response.error(e.getErrorCode().name(), e.getMessage(), UUID.randomUUID().toString());
	}

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorBody> handleBiz(BusinessException ex) {
		HttpStatus status = HttpStatus.CONFLICT;
		if (ex.getCode() == InventoryErrorCode.RESERVATION_NOT_FOUND
			|| ex.getCode() == InventoryErrorCode.STOCK_NOT_FOUND) {
			status = HttpStatus.NOT_FOUND;
		} else if (ex.getCode() == InventoryErrorCode.INVALID_PARAM) {
			status = HttpStatus.BAD_REQUEST;
		}
		ErrorBody body = new ErrorBody();
		body.setCode(ex.getCode().name());
		body.setMessage(ex.getMessage());
		return new ResponseEntity<>(body, status);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorBody> handleOther(Exception ex) {
		ErrorBody body = new ErrorBody();
		body.setCode("INTERNAL_ERROR");
		body.setMessage("internal error");
		return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	public static class ErrorBody {

		private String code;
		private String message;

		public String getCode() {
			return code;
		}

		public void setCode(String code) {
			this.code = code;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}
	}
}


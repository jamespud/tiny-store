package com.github.spud.tinystore.infrastructure.rpc.order.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单域 RPC 响应（匹配 order 域 {code, msg, data} 结构）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderRpcResponse<T> {
    private int code;
    private String msg;
    private T data;
}

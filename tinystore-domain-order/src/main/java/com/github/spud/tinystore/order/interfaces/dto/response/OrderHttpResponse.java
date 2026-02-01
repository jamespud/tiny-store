package com.github.spud.tinystore.order.interfaces.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Order 域统一 HTTP 响应封装
 * 严格保持现有 API 契约：{code, msg, data}
 * 成功态 code 固定为 0
 *
 * @param <T> 响应数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderHttpResponse<T> {
    
    /**
     * 响应码：0 表示成功，其他表示失败
     */
    private int code;
    
    /**
     * 响应消息
     */
    private String msg;
    
    /**
     * 响应数据
     */
    private T data;
    
    /**
     * 成功响应（带数据）
     */
    public static <T> OrderHttpResponse<T> ok(T data) {
        return new OrderHttpResponse<>(0, "OK", data);
    }
    
    /**
     * 成功响应（带自定义消息与数据）
     */
    public static <T> OrderHttpResponse<T> ok(String msg, T data) {
        return new OrderHttpResponse<>(0, msg, data);
    }
    
    /**
     * 成功响应（仅消息，无数据）
     */
    public static <T> OrderHttpResponse<T> ok(String msg) {
        return new OrderHttpResponse<>(0, msg, null);
    }
    
    /**
     * 失败响应
     */
    public static <T> OrderHttpResponse<T> fail(int code, String msg) {
        return new OrderHttpResponse<>(code, msg, null);
    }
    
    /**
     * 失败响应（带数据）
     */
    public static <T> OrderHttpResponse<T> fail(int code, String msg, T data) {
        return new OrderHttpResponse<>(code, msg, data);
    }
}

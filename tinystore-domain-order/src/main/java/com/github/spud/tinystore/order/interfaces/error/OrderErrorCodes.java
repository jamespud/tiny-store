package com.github.spud.tinystore.order.interfaces.error;

/**
 * 统一错误码常量，保持 ORDER- 前缀 + 数字分段。
 * 参数/校验 400x，鉴权 401x，权限 403x，资源 404x，并发 409x，语义 422x，系统 500x。
 */
public final class OrderErrorCodes {
  private OrderErrorCodes() {}

  // 400x 参数 & 校验
  public static final String VALIDATION_ERROR = "ORDER-4000";
  public static final String BAD_REQUEST_GENERIC = "ORDER-4001";

  // 401x 鉴权
  public static final String UNAUTHORIZED = "ORDER-4010";

  // 403x 权限
  public static final String FORBIDDEN = "ORDER-4030";

  // 404x 资源缺失
  public static final String NOT_FOUND = "ORDER-4040";

  // 409x 并发/版本冲突
  public static final String CONFLICT = "ORDER-4090";

  // 422x 语义/状态非法
  public static final String UNPROCESSABLE = "ORDER-4220";

  // 500x 系统内部
  public static final String INTERNAL_ERROR = "ORDER-5000";
  public static final String UNKNOWN = "ORDER-UNKNOWN";
}

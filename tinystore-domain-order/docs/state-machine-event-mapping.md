# 订单状态机：命令 → 事件映射与 Guard

本文对 tinystore-domain-order 的订单核心流转（CoreFlowStatus）进行命令→事件（OrderEvent）映射与 Guard 约束说明，并标注关键 Action 的职责边界。状态机实现参考 `OrderStateMachineConfig` 与 `OrderEvent`。

- 核心状态集合（CoreFlowStatus）：`PENDING_PAYMENT` → `PAID` → `ACCEPTED` → `FULFILLING` → `COMPLETED`，以及终止态 `CANCELLED`
- 事件集合（OrderEvent）：`PAYMENT_SUCCEEDED`, `PAYMENT_FAILED`, `PAYMENT_TIMEOUT`, `USER_CANCELLED`, `SYSTEM_CANCELLED`, `MERCHANT_ACCEPTED`, `MERCHANT_CANCELLED`, `FULFILLMENT_STARTED`, `GOODS_SHIPPED`, `GOODS_DELIVERED`, `GOODS_RECEIVED`, `AUTO_RECEIVE_TIMEOUT`, `GOODS_REJECTED`, `FULFILLMENT_TIMEOUT`
- 维度说明：除 `core_flow` 外，还存在 payment/fulfillment/after_sale 等维度在聚合内更新（不一定驱动 core_flow 跃迁）

## 1) 命令 → 事件 → 状态

- PaymentSucceededCommand
  - 事件：`PAYMENT_SUCCEEDED`
  - 源状态：`PENDING_PAYMENT`
  - 目标状态：`PAID`
  - Guard：订单处于待支付；未被取消；支付回调幂等通过

- UnpaidTimeoutCancelCommand
  - 事件：`PAYMENT_TIMEOUT`
  - 源状态：`PENDING_PAYMENT`
  - 目标状态：`CANCELLED`
  - Guard：到期未支付；调度幂等键校验通过

- ApplyCancelCommand / CancelOrderCommand
  - 事件：`USER_CANCELLED`
  - 场景A（待支付取消）：
    - 源状态：`PENDING_PAYMENT` → 目标：`CANCELLED`
    - Guard：订单未关闭；请求者为用户；幂等键通过
  - 场景B（已支付后申请取消）：不直接驱动 core_flow，进入取消申请子流程（待商家审批），详见取消审批命令

- ApproveCancelOrderCommand（商家同意取消）
  - 事件：`MERCHANT_CANCELLED`
  - 可能的源状态：`PAID` | `ACCEPTED` | `FULFILLING`
  - 目标状态：`CANCELLED`
  - Guard：存在有效的取消申请；订单未终态；并发版本校验通过

- RejectCancelOrderCommand（商家拒绝取消）
  - 事件：无 core_flow 变更（维度内记录审核结果）
  - Guard：存在有效的取消申请；订单未终态

- MerchantAcceptCommand
  - 事件：`MERCHANT_ACCEPTED`
  - 源状态：`PAID`
  - 目标状态：`ACCEPTED`
  - Guard：商家有处理权限；订单未终态

- ShipOrderCommand
  - 事件：`FULFILLMENT_STARTED`
  - 源状态：`ACCEPTED`
  - 目标状态：`FULFILLING`
  - 补充：随后可发 `GOODS_SHIPPED`（内部事件，不改变 core_flow）更新发货包裹信息

- DeliveredCommand（物流妥投）
  - 事件：`GOODS_DELIVERED`（内部事件）
  - 源状态：`FULFILLING`
  - 目标状态：保持 `FULFILLING`（不直接完成，等待用户收货/超时自动收货）
  - Guard：物流回调幂等通过；包裹存在且处于已发货/在途

- ConfirmReceiptCommand（用户确认收货）
  - 事件：`GOODS_RECEIVED`
  - 源状态：`FULFILLING`
  - 目标状态：`COMPLETED`
  - Guard：请求用户为下单用户；未处于售后冻结导致禁止收货

- AutoCompleteCommand（超时自动收货）
  - 事件：`AUTO_RECEIVE_TIMEOUT`
  - 源状态：`FULFILLING`
  - 目标状态：`COMPLETED`
  - Guard：达到自动收货时点；期间无进行中的售后

- MoveToAwaitFulfillmentCommand（保障性转待履约）
  - 事件：`MERCHANT_ACCEPTED`（作为保障性接单）
  - 源状态：`PAID`
  - 目标状态：`ACCEPTED`
  - Guard：支付已成功但长时间未接单；保障任务幂等

- RefundSucceededCommand（退款成功）
  - 事件：不直接驱动 core_flow（payment/after_sale 维度更新）
  - Guard：退款回调幂等通过；匹配对应子单/行级售后

## 2) 事件 → 状态矩阵（按状态机实现）

- `PENDING_PAYMENT` → `PAID`：`PAYMENT_SUCCEEDED`
- `PENDING_PAYMENT` → `CANCELLED`：`PAYMENT_FAILED` | `PAYMENT_TIMEOUT` | `USER_CANCELLED` | `SYSTEM_CANCELLED`
- `PAID` → `ACCEPTED`：`MERCHANT_ACCEPTED`
- `PAID` → `CANCELLED`：`MERCHANT_CANCELLED`
- `ACCEPTED` → `FULFILLING`：`FULFILLMENT_STARTED`
- `FULFILLING`（内部进展不改态）：`GOODS_SHIPPED` | `GOODS_DELIVERED`
- `FULFILLING` → `COMPLETED`：`GOODS_RECEIVED` | `AUTO_RECEIVE_TIMEOUT`
- `FULFILLING` → `CANCELLED`：`GOODS_REJECTED` | `MERCHANT_CANCELLED` | `SYSTEM_CANCELLED`

## 3) Guard 原则
- 合法前置状态：仅允许定义的源状态触发对应事件；否则事件不被接受
- 幂等：所有外部/内部回调（支付、物流、退款、定时）必须携带可验证的幂等键
- 互斥：取消/售后与发货/收货具有互斥关系；处于终态（CANCELLED/COMPLETED）拒绝任何进一步事件
- 并发：使用乐观锁（版本号）控制并发修改，冲突返回 409/业务码

## 4) Action 职责边界（概览）
- 原子化更新聚合内各维度状态与必要字段（core_flow/payment/fulfillment/after_sale）
- 仅在成功跃迁后写入 Outbox 事件（类型与载荷见出箱规范）；失败不写入
- 写入审计日志（命令上下文、操作者、traceId、事件/状态）
- 不进行外部IO调用（除 Outbox 持久化）；业务校验放在应用服务层

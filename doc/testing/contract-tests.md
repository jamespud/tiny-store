# 契约测试（Order–Inventory / Order–Payment）

- 覆盖：成功、失败、重试、超时；重复投递与幂等验证。
- 工具：Testcontainers + Embedded Kafka + WireMock（支付沙箱）。
- 验收：无跨域不一致，补偿路径闭环。

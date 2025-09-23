# 认证与授权（Auth & RBAC）

- JWT Bearer：短期访问票据（15 min）+ 刷新（7 days）；JWKs 轮转。
- RBAC：CUSTOMER/ADMIN/OPS；服务内方法级权限。
- 服务间：mTLS 或服务签名 JWT；最小权限 DB 账户。

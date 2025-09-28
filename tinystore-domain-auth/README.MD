# Tinystore Authorization Service

本模块实现字节商城的自建 OAuth2/OIDC 授权服务器，支持 C 端用户和内部 M2M 服务集成。

## Scope 约定

| 范围 | 说明 | 默认授予 | 需用户同意 |
| ---- | ---- | -------- | ---------- |
| `openid` | OIDC 基础标识 | ✅ | ❌ |
| `user.profile` | 基础资料（昵称、头像） | ✅ | ❌ |
| `user.phone` | 手机号 | ❌ | ✅ |
| `user.address` | 收货地址 | ❌ | ✅ |
| `user.follow` | 关注关系 | ❌ | ✅ |
| `user.payment` | 支付信息 | ❌ | ✅ |
| `svc.*` | 内部服务调用（如 `svc.order.read`） | N/A | N/A |

## 令牌策略

- C 端 Access Token/ID Token：2 小时，Refresh Token：30 天
- M2M Access Token：5 分钟，不下发 Refresh Token
- Access Token 携带 `authorities` + `roles` Claim；ID Token 仅携带身份属性（`sub` = `mall_user.id`）
- RP 发起注销：`/oauth2/logout`（OIDC End Session）
- Token Revocation：`POST /oauth2/revoke` 或管理员接口 `/api/admin/tokens/revoke?user_id=...`

## OTP 登录流程（开发态）

1. `POST /api/auth/otp/send` 提交手机号，Stub 返回固定验证码 `123456`
2. `POST /login/otp`（或 `/api/auth/otp/verify`）提交手机号及验证码
3. 首次登录自动创建本地商城用户并授予 `user.profile`

## 必备配置

- Issuer（按环境）：`https://auth.dev.tinystore.com` / `https://auth.test.tinystore.com` / `https://auth.tinystore.com`
- JWKS：`/.well-known/jwks.json`
- CORS（dev）：`http://localhost:3000`、`http://localhost:8080`、`https://*.dev.tinystore.com`
- 令牌 TTL、OTP TTL 可通过 `tinystore.auth.tokens.*`、`tinystore.auth.otp.ttl` 配置

## 运维

- Flyway V2 创建 `mall_user`、`login_otp` 表
- 管理端可通过 `/api/admin/tokens/revoke` 结合 `rt_version` 进行批量下线
- 生产环境需将 JKS 替换为 KMS/Vault 注入，禁用默认口令

更多架构细节见 `TEMPLATE.MD`。

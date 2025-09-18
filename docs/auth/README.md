# TinyStore Auth 启动与联调指南（OIDC + SAS）

本指南帮助你在本地快速启动授权服务器（Spring Authorization Server, OIDC Provider）并完成与网关/下游服务的联调验证。

## 1. 预置条件

- JDK 17、Maven 3.9+
- 本地 PostgreSQL 可用（推荐 DB: tinystore / 用户: postgres / 密码: postgres）
- 端口未占用：9000（auth）、8080（gateway）

## 2. 初始化密钥（开发态）

授权服务器通过 JKS 提供 RSA 签名密钥（生产请使用 KMS/Vault）。在模块 `tinystore-domain-auth/src/main/resources/keystore/` 下生成 `tinystore-auth.jks`：

```bash
keytool -genkeypair -alias auth -keyalg RSA -keysize 2048 -storetype JKS \
  -keystore tinystore-auth.jks -validity 3650 \
  -storepass changeit -keypass changeit \
  -dname "CN=tinystore, OU=dev, O=tinystore, L=City, S=State, C=CN"
```

生成后无需改动配置（默认路径即 `classpath:keystore/tinystore-auth.jks`）。

## 3. 数据库与迁移

`tinystore-domain-auth` 已启用 Flyway，首次启动会自动创建 SAS 所需表：

- oauth2_authorization
- oauth2_authorization_consent
- oauth2_registered_client

如需要自定义库连接，可修改 `tinystore-domain-auth/src/main/resources/application.yml` 的 `spring.datasource.*`。

## 4. 启动服务

建议在工程根目录执行分模块启动：

1) 启动授权服务器（:9000）

```bash
mvn -pl tinystore-domain-auth -am spring-boot:run
```

验证端点：

- OIDC Discovery: http://localhost:9000/.well-known/openid-configuration
- JWKS: http://localhost:9000/.well-known/jwks.json
- 登录页：访问授权流程时自动跳转 /login（开发账号：user/admin，密码：changeit）

2) 启动网关（:8080）与任一资源服务（如 inventory）

```bash
mvn -pl tinystore-domain-gateway -am spring-boot:run
mvn -pl tinystore-domain-inventory -am spring-boot:run
```

这些模块默认已开启 Resource Server 并配置了 `issuer-uri=http://localhost:9000`。

## 5. 客户端注册与默认配置

系统在启动时初始化了三个客户端（JDBC 存储）：

- tinystore-web（公共客户端）
  - 授权类型：authorization_code + PKCE（含 refresh_token）
  - 回调：
    - http://localhost:3000/callback
    - http://localhost:8080/login/oauth2/code/tinystore-web
  - scopes：openid, profile, email, inventory.read, order.read

- tinystore-internal（机密客户端，M2M）
  - 授权类型：client_credentials
  - secret：读取环境变量 `TINYSTORE_INTERNAL_CLIENT_SECRET`（未设置时为 changeit）
  - scopes：inventory.read, inventory.write, order.read, order.write

- tinystore-tool（机密客户端）
  - 授权类型：authorization_code（可 refresh_token）
  - 回调：http://localhost:8081/callback
  - scopes：openid, profile, email, admin

注意：生产环境请务必替换密钥与机密，并限制回调域名。

## 6. 联调流程示例

### 6.1 Authorization Code + PKCE（Web/SPA）

1) 生成 `code_verifier` 与 `code_challenge`（可用任意 OAuth PKCE 工具）
2) 浏览器打开授权请求（示例）：

```
GET http://localhost:9000/oauth2/authorize?response_type=code
  &client_id=tinystore-web
  &redirect_uri=http://localhost:3000/callback
  &scope=openid%20profile%20email%20inventory.read
  &code_challenge=...&code_challenge_method=S256
  &state=xyz
```

3) 登录并同意（如配置），回跳到 redirect_uri，带上 `code` 和 `state`
4) 后端以 `code` + `code_verifier` 交换 token：

```bash
curl -X POST http://localhost:9000/oauth2/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=authorization_code' \
  -d 'client_id=tinystore-web' \
  -d 'redirect_uri=http://localhost:3000/callback' \
  -d 'code=XXXX' \
  -d 'code_verifier=YOUR_CODE_VERIFIER'
```

5) 拿到 `access_token` 后调用网关受保护接口：

```bash
curl -H 'Authorization: Bearer ACCESS_TOKEN' http://localhost:8080/api/your-endpoint
```

### 6.2 Client Credentials（服务间调用）

```bash
CLIENT_ID=tinystore-internal
CLIENT_SECRET=${TINYSTORE_INTERNAL_CLIENT_SECRET:-changeit}

curl -X POST http://localhost:9000/oauth2/token \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials' \
  -d 'scope=inventory.read'
```

得到 `access_token` 后同样调用网关资源接口。

### 6.3 刷新令牌（轮换）

```bash
curl -X POST http://localhost:9000/oauth2/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=refresh_token' \
  -d 'client_id=tinystore-web' \
  -d 'refresh_token=YOUR_REFRESH_TOKEN'
```

## 7. 常见问题

- 401/invalid_token：确认 `issuer-uri`、系统时间、JWKS 可访问；检查 token 是否过期
- 无法生成 token：检查数据库连通与 Flyway 是否成功迁移
- JWKS 为空或密钥错误：检查 JKS 是否生成、配置路径/密码是否对应

## 8. 生产注意事项（概要）

- 使用 KMS/Vault 托管密钥，启用密钥轮换（kid + JWKS）
- 缩短 access token TTL，启用刷新令牌轮换
- 严格限制回调域名/CORS，开启 TLS 全链路
- 对登录/授权/刷新端点做限流与审计

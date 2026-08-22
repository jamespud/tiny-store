#!/usr/bin/env python3
"""
生成一个符合微信支付 v3 回调规范、可验签/可解密的“支付成功”回调样例。

用途：在本地/测试中驱动 tiny-store 的 A4 回调验签与真实渠道接入。
注意：这里用的是临时演示密钥（APIv3 key 与 RSA 平台密钥），仅用于测试；
      真实对接微信支付时请替换为您商户号下的真实 APIv3 密钥与平台证书。

用法：
    python3 generate_wechat_callback.py
输出（写入当前目录）：
    wechat-pay-callback-sample.json   # 回调 POST 报文（resource 为 AES-256-GCM 密文）
    wechat-pay-callback-sample.md     # 样例 + 请求头 + 演示密钥 + 验签/解密说明
"""

from __future__ import annotations

import base64
import datetime
import json
import os
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.x509.oid import NameOID


# ── 1. 演示密钥 ──────────────────────────────────────────────────────────────
# APIv3 密钥：必须 32 字节（微信要求 32 位）。这里用固定占位字符串，仅演示。
API_V3_KEY = b"0123456789abcdef0123456789abcdef"  # 32 bytes
MCHID = "1900001234"
APPID = "wx-tinystore-demo"

# 固定演示 RSA 平台私钥（2048，仅测试），使样例可复现、Java 测试可稳定引用。
FIXED_PRIVATE_KEY_PEM = """-----BEGIN PRIVATE KEY-----
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDPmo4PKM5SLgfV
CQB1o1zCWl21ExBRkPQLP7dqrNbfFecsM/5HedOoPD0CCRrkTz728vDnX1SWNRDK
LrUQObyAuMBEUSJHXLSUF3qqnsuFt+wsQDZq8or6vSjxE9gcEDUAHMe/5vz4yZjI
L9dYyhC18th5S7cA/LEmu4uY7Fg0U627Bon7jzdJ4wKYNJorV8wO0LsC/CoXu2n5
SVbPJdv8lFHSKM/YUgO8jFDdJKPmz3Qo+ie4fDgc/9oGc2kaydJv3nsBG+Bh2djg
DVXhWXxf3zQ10K+UCklVtBBRR4rqGylWoVcZg5wK+vox61C1C41ZQIhc5OQSmciR
hOrWgxk7AgMBAAECggEAD6i+x7yc42ypa+julJuqkYEIyccTAglcVG+NQYn2oy50
biHLL6UBfBJ0dO+ENxhrIsFsaRIIaqlnXM4XxjjfU8ObwmDETycE+plSyAU2S3Mg
vgMR7RCNFHX+3UBRRWdLjXieRdwB1No6oKRLKrKt1ADp/IGONOWFOetc5OoJioNq
h88xFdl6HWVOmPnTV6BDI77409amI32ZLC9oHsWYEWZLqr/UPg1DGNA4FjkmlWWJ
u/FPU10AAQmSbvoIbuVzkwVrojfIen/493x3FCs3iEu0H3Cdq9bxzZk53tHME0Nv
jZnS1jW3FVcEgIow1PuKLNQNz8UcHRwA1bV+dKnAIQKBgQDp5XT+F8FXPB06OWlo
OHu1sg8esTC3sQ4ILPnFEW+z1Ycqp1C2NL7Vo/4cmUcNQNez7AL3CR3wpGjITYm6
2hqwiJPgKPDkEV0moGb2safKP6Nu6Gb1vh2K7A3e0qK+grKq1LhyzBgZ/RcIDtKx
a4lk1GaxwajaZBDQcql0AUB+QwKBgQDjOQOI75UeXCxj45aHUmjoeouVnUITGx3U
MHijRTNwDOW9XUazPsESpRsn85D4mc8OkR329aS9/fsLnhj5tbqATccrlx6FSxzH
rpKGgvPBwZtHVPhbzTFUA6UMCHuzzVDNcjLj0T2pUrot4zlrKcurlv+/ajsclwSJ
I4kdTXbVqQKBgQCkstKC6a9XAhmCmlLC9KMH+m3zKsonTyGWWDU4s2J4u28GdfK7
msCWJ3gUI0qI5pP9Ob+MvHq8rMir4w2M6W/JxyJ5wdp+fkudm21lYQvJuq4Wtsdp
W4np/PZ3ZNL8W4P8DvYiv9xo0HKbVfS+wf5pZbc6jCeeQMAmR9dSvF7xiQKBgAQA
QwGsRlHsiZOQtFvZpaNCVSbjSACSH6pW2Cj31PoKIBl/hPkvB7NOWPYRIeJewvic
sYxhsu7tg/gmZoYvHwOXWwR3esAaHH2fo1DfCW/F+vf8lQr4x/+UuNlHZPY7jUqw
0hiU3KMYo9KfB6nNaJqy4/n44uWT+y53A7kSXh9RAoGBAIL4we7c4dVRsA7y0XnM
dqt8j2vh7c2b6Me1zstTewAkQN8a+sZQwdTiR3606PXV1DS0z6sfNWLHWtrqigHj
1Pe6r/xG3dMmHoyp/mISSBODVK7h42GNesE+SNX1LD0k26ddcGZ1AIrDnSmPPgaV
F3eZonmKi2/f20jUxpfP4YRk
-----END PRIVATE KEY-----"""
private_key = serialization.load_pem_private_key(FIXED_PRIVATE_KEY_PEM.encode(), password=None)

# 固定证书序列号（满足 "PUB_KEY_ID_数字串" 之外的真实证书序列号形态）
FIXED_SERIAL = 0x4B1B261F3D30D4903721FA7A2395F815
subject = issuer = x509.Name([
    x509.NameAttribute(NameOID.COMMON_NAME, "Tinystore WeChat Platform Test"),
])
cert = (
    x509.CertificateBuilder()
    .subject_name(subject)
    .issuer_name(issuer)
    .public_key(private_key.public_key())
    .serial_number(FIXED_SERIAL)
    .not_valid_before(datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(days=1))
    .not_valid_after(datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=365))
    .sign(private_key, hashes.SHA256())
)
SERIAL = format(cert.serial_number, "x").upper()
PUB = cert.public_key().public_bytes(
    serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo
).decode()
PRIV = private_key.private_bytes(
    serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
    serialization.NoEncryption(),
).decode()
CERT_PEM = cert.public_bytes(serialization.Encoding.PEM).decode()


# ── 2. 业务资源明文（resource.ciphertext 解密后得到）────────────────────────
out_trade_no = "TS202608231234567890"
amount_total = 1000  # 分，对应 seed SKU_A 1000 分
resource_plaintext = {
    "mchid": MCHID,
    "appid": APPID,
    "out_trade_no": out_trade_no,
    "transaction_id": "4200002486202608230000000001",
    "trade_type": "APP",
    "trade_state": "SUCCESS",
    "trade_state_desc": "支付成功",
    "bank_type": "OTHERS",
    "attach": "tinystore-trade:" + out_trade_no,
    "success_time": "2026-08-23T12:00:00+08:00",
    "payer": {"openid": "oUpF8uMuAJO_M2pxb1Q9zNjWeS6o"},
    "amount": {
        "total": amount_total,
        "payer_total": amount_total,
        "currency": "CNY",
        "payer_currency": "CNY",
    },
}
plain_json = json.dumps(resource_plaintext, ensure_ascii=False, separators=(",", ":"))


# ── 3. AES-256-GCM 加密 resource ─────────────────────────────────────────────
nonce = b"TinystoreNce12"       # 12 字节随机串
aad = b""                       # associated_data（可为空）
ciphertext_b64 = base64.b64encode(
    AESGCM(API_V3_KEY).encrypt(nonce, plain_json.encode("utf-8"), aad)
).decode()


# ── 4. 回调通知报文（回调 POST 请求体，需严格保留此字符串用于验签）──────────
notify_body = json.dumps({
    "id": "EV-" + out_trade_no,
    "create_time": "2026-08-23T12:00:00+08:00",
    "resource_type": "encrypt-resource",
    "event_type": "TRANSACTION.SUCCESS",
    "summary": "支付成功",
    "resource": {
        "original_type": "transaction",
        "algorithm": "AEAD_AES_256_GCM",
        "ciphertext": ciphertext_b64,
        "associated_data": aad.decode(),
        "nonce": nonce.decode(),
    },
}, ensure_ascii=False, separators=(",", ":"))


# ── 5. 回调请求头（RSA-SHA256 验签）─────────────────────────────────────────
ts = "1787532000"             # Wechatpay-Timestamp（Unix 秒）
nonce_header = "c0kZP6cSbveFpn0U"  # Wechatpay-Nonce
message = f"{ts}\n{nonce_header}\n{notify_body}\n"
signature = base64.b64encode(
    private_key.sign(message.encode("utf-8"), padding.PKCS1v15(), hashes.SHA256())
).decode()

headers = {
    "Wechatpay-Serial": SERIAL,
    "Wechatpay-Timestamp": ts,
    "Wechatpay-Nonce": nonce_header,
    "Wechatpay-Signature": signature,
}


# ── 6. 输出 ─────────────────────────────────────────────────────────────────
out_dir = os.path.dirname(os.path.abspath(__file__))
with open(os.path.join(out_dir, "wechat-pay-callback-sample.json"), "w", encoding="utf-8") as f:
    f.write(notify_body + "\n")

md = f"""# 微信支付 v3 支付成功回调样例（测试可用）

> 依据微信支付 v3《回调通知》规范生成：POST 报文 resource 为 AES-256-GCM 密文，请求头为
> RSA-SHA256 验签。**使用的均为演示密钥**，仅用于本地/测试；真实对接请替换为您商户号的真实
> APIv3 密钥与平台证书。

## 请求 URL 与方法

`POST <notify_url>`（商户下单时传入的 notify_url）

## 请求头（验签）

```json
{json.dumps(headers, indent=2, ensure_ascii=False)}
```

## 请求体（回调报文）

```json
{json.dumps(json.loads(notify_body), indent=2, ensure_ascii=False)}
```

## 演示密钥

- **APIv3 密钥**（AES-256-GCM 解密 resource）：`{API_V3_KEY.decode()}`
- **resource.nonce**：`{nonce.decode()}`
- **resource.associated_data**：`{aad.decode()}`
- **Wechatpay-Serial**：`{SERIAL}`
- **微信支付平台证书（公钥，用于验签 Wechatpay-Signature）**：

```text
{CERT_PEM}
```

- **平台私钥**（仅用于本样例生成签名；真实场景由微信侧持有）：

```text
{PRIV}
```

## resource 解密后的明文（Outer）

```json
{json.dumps(resource_plaintext, indent=2, ensure_ascii=False)}
```

## 验证方式

用 `cryptography`：

```python
import base64, json
from cryptography import x509
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

body = json.load(open("wechat-pay-callback-sample.json", encoding="utf-8"))

# 1) 验签：message = timestamp\\nnonce\\nbody\\n
msg = f"{{headers['Wechatpay-Timestamp']}}\\n{{headers['Wechatpay-Nonce']}}\\n{notify_body}\\n"
pub = x509.load_pem_x509_certificate(CERT_PEM.encode()).public_key()
pub.verify(base64.b64decode(headers["Wechatpay-Signature"]), msg.encode(), padding.PKCS1v15(), hashes.SHA256())

# 2) 解密 resource
plain = AESGCM(API_V3_KEY).decrypt(
    body["resource"]["nonce"].encode(),
    base64.b64decode(body["resource"]["ciphertext"]),
    body["resource"]["associated_data"].encode(),
)
print(json.loads(plain))
```

运行生成脚本（重新生成）：`python3 generate_wechat_callback.py`
"""
with open(os.path.join(out_dir, "wechat-pay-callback-sample.md"), "w", encoding="utf-8") as f:
    f.write(md)

print(json.dumps({"body": json.loads(notify_body), "headers": headers,
                  "api_v3_key": API_V3_KEY.decode(), "serial": SERIAL,
                  "private_pem": PRIV, "cert_pem": CERT_PEM}, ensure_ascii=False))

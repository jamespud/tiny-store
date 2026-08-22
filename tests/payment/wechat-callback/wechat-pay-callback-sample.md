# 微信支付 v3 支付成功回调样例（测试可用）

> 依据微信支付 v3《回调通知》规范生成：POST 报文 resource 为 AES-256-GCM 密文，请求头为
> RSA-SHA256 验签。**使用的均为演示密钥**，仅用于本地/测试；真实对接请替换为您商户号的真实
> APIv3 密钥与平台证书。

## 请求 URL 与方法

`POST <notify_url>`（商户下单时传入的 notify_url）

## 请求头（验签）

```json
{
  "Wechatpay-Serial": "4B1B261F3D30D4903721FA7A2395F815",
  "Wechatpay-Timestamp": "1787532000",
  "Wechatpay-Nonce": "c0kZP6cSbveFpn0U",
  "Wechatpay-Signature": "p3Po/bctnXmOrNDs6m+vvlx8luS3n18E/QVR/nh7r5jjPB3m3pBSFT8J50H/YUJI9wPL7iS7elGmORFJ2qoaJfeGdOgoi2evvqtvpa7cknWFo2C5H6i5fqJm/WLTWy868htIuvl2MiqP7h+LY+aLc7Rqn596dBZgtWaDQLW4G5fUADvlHkRAU5PMK4e53gRFFZrT9ExH0uegsivERBMD0Yi065KB6O7Aw2nYDTvSOuRJBlvYzlCrKo3SlDQo7wPCkpAmY5ZRciim+66y0X2RTJDhh9MJMBvFq3N7e7N/s3dXd2fzLm2wNorbnlK2vQBYT6QALVzIAA+Vfpc2HpwM2Q=="
}
```

## 请求体（回调报文）

```json
{
  "id": "EV-TS202608231234567890",
  "create_time": "2026-08-23T12:00:00+08:00",
  "resource_type": "encrypt-resource",
  "event_type": "TRANSACTION.SUCCESS",
  "summary": "支付成功",
  "resource": {
    "original_type": "transaction",
    "algorithm": "AEAD_AES_256_GCM",
    "ciphertext": "vV6cSAjRIUg+0FP4GzZNx7IaUHu+srEQkBLtzzKcM8JOntmIMOKfXivc3sWPzmOuVGUj+JuyeqTJskTFSRBAovj/+SU5uMMxrMtMAr1QC7bk17dyr2VQjxYqtJfecDk3Rb60jAmQ6JlYoR2sda5dYjxfJJfBl8CAC5w/cWlne1VLVIl2s/Ztgvyd353NdDzPYww5++UIKBWNcGc7UsotivFwfqqX6Hzlqx6UeeHhGEV+ZuLYN+krFc6mpA9MbMac8TM6+Z9L8jFZCMqVuqdefzbcZU8fJbxtg/3O6wcnvx6RReY0fH0YZbp2P9+nySr8hBbwNv1zx3qZFtumcE/jOQzzTFBE8fDHnSM90ZTltRYVmGJ0/GHc8zysI4WmVxNtY6ht4+08sK0qTfB/nG1et0pQh4wTmKarnEVauj0ls2kuUPetxMRkk7wVT/bzAbY6xufVNXipKAKSFw3iMbkMQPHCgURMWTYFT+fDMQsj7yUUDT9MXo1qfruRkBr2WeltlELL9o+6MDF6/52PkytVfTLMNvMavpoGghYoENEe4eLEQg7qWiGGAtzLplc6E3UOJsIhHKKy4+eHMNleUvDYj003txc7l08RmUAPsXxPC2DNjvpSOBop9qzN",
    "associated_data": "",
    "nonce": "TinystoreNce12"
  }
}
```

## 演示密钥

- **APIv3 密钥**（AES-256-GCM 解密 resource）：`0123456789abcdef0123456789abcdef`
- **resource.nonce**：`TinystoreNce12`
- **resource.associated_data**：``
- **Wechatpay-Serial**：`4B1B261F3D30D4903721FA7A2395F815`
- **微信支付平台证书（公钥，用于验签 Wechatpay-Signature）**：

```text
-----BEGIN CERTIFICATE-----
MIIC2jCCAcKgAwIBAgIQSxsmHz0w1JA3Ifp6I5X4FTANBgkqhkiG9w0BAQsFADAp
MScwJQYDVQQDDB5UaW55c3RvcmUgV2VDaGF0IFBsYXRmb3JtIFRlc3QwHhcNMjYw
ODIxMjAyOTMwWhcNMjcwODIyMjAyOTMwWjApMScwJQYDVQQDDB5UaW55c3RvcmUg
V2VDaGF0IFBsYXRmb3JtIFRlc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK
AoIBAQDPmo4PKM5SLgfVCQB1o1zCWl21ExBRkPQLP7dqrNbfFecsM/5HedOoPD0C
CRrkTz728vDnX1SWNRDKLrUQObyAuMBEUSJHXLSUF3qqnsuFt+wsQDZq8or6vSjx
E9gcEDUAHMe/5vz4yZjIL9dYyhC18th5S7cA/LEmu4uY7Fg0U627Bon7jzdJ4wKY
NJorV8wO0LsC/CoXu2n5SVbPJdv8lFHSKM/YUgO8jFDdJKPmz3Qo+ie4fDgc/9oG
c2kaydJv3nsBG+Bh2djgDVXhWXxf3zQ10K+UCklVtBBRR4rqGylWoVcZg5wK+vox
61C1C41ZQIhc5OQSmciRhOrWgxk7AgMBAAEwDQYJKoZIhvcNAQELBQADggEBAE9w
+XN4uO+tZ+he2esrt8k8lyX57ZDGqbnPbWKBmSRK3C+gMhZnMqtGUOvlw/KvWejP
fyD+eJngUGScog3nWrUb0rKVrV5VV6B/UcH+geshUNAKSaU0gDAGXcz6n3jbeiZk
aKHwrassPTnl4Wbk92IVtQuNomOEkC2Ay+XcabC294Y+PUbh4Nu8CIJvyY0E1YAC
xvOCbNCk+SZkyg5MIuYaDGS13qOo+wzEiaSs3alrJNtllQWuAW62FeiodgiPe8lH
ManIJ9Hdqx0AhunMykvV5yhV0ddwvCyJXM6XrTVDvEQ7Fmo2//aaZCEsqA2e6wtx
zHMm6wJBME+vnnnCZcM=
-----END CERTIFICATE-----

```

- **平台私钥**（仅用于本样例生成签名；真实场景由微信侧持有）：

```text
-----BEGIN PRIVATE KEY-----
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
-----END PRIVATE KEY-----

```

## resource 解密后的明文（Outer）

```json
{
  "mchid": "1900001234",
  "appid": "wx-tinystore-demo",
  "out_trade_no": "TS202608231234567890",
  "transaction_id": "4200002486202608230000000001",
  "trade_type": "APP",
  "trade_state": "SUCCESS",
  "trade_state_desc": "支付成功",
  "bank_type": "OTHERS",
  "attach": "tinystore-trade:TS202608231234567890",
  "success_time": "2026-08-23T12:00:00+08:00",
  "payer": {
    "openid": "oUpF8uMuAJO_M2pxb1Q9zNjWeS6o"
  },
  "amount": {
    "total": 1000,
    "payer_total": 1000,
    "currency": "CNY",
    "payer_currency": "CNY"
  }
}
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

# 1) 验签：message = timestamp\nnonce\nbody\n
msg = f"{headers['Wechatpay-Timestamp']}\n{headers['Wechatpay-Nonce']}\n{"id":"EV-TS202608231234567890","create_time":"2026-08-23T12:00:00+08:00","resource_type":"encrypt-resource","event_type":"TRANSACTION.SUCCESS","summary":"支付成功","resource":{"original_type":"transaction","algorithm":"AEAD_AES_256_GCM","ciphertext":"vV6cSAjRIUg+0FP4GzZNx7IaUHu+srEQkBLtzzKcM8JOntmIMOKfXivc3sWPzmOuVGUj+JuyeqTJskTFSRBAovj/+SU5uMMxrMtMAr1QC7bk17dyr2VQjxYqtJfecDk3Rb60jAmQ6JlYoR2sda5dYjxfJJfBl8CAC5w/cWlne1VLVIl2s/Ztgvyd353NdDzPYww5++UIKBWNcGc7UsotivFwfqqX6Hzlqx6UeeHhGEV+ZuLYN+krFc6mpA9MbMac8TM6+Z9L8jFZCMqVuqdefzbcZU8fJbxtg/3O6wcnvx6RReY0fH0YZbp2P9+nySr8hBbwNv1zx3qZFtumcE/jOQzzTFBE8fDHnSM90ZTltRYVmGJ0/GHc8zysI4WmVxNtY6ht4+08sK0qTfB/nG1et0pQh4wTmKarnEVauj0ls2kuUPetxMRkk7wVT/bzAbY6xufVNXipKAKSFw3iMbkMQPHCgURMWTYFT+fDMQsj7yUUDT9MXo1qfruRkBr2WeltlELL9o+6MDF6/52PkytVfTLMNvMavpoGghYoENEe4eLEQg7qWiGGAtzLplc6E3UOJsIhHKKy4+eHMNleUvDYj003txc7l08RmUAPsXxPC2DNjvpSOBop9qzN","associated_data":"","nonce":"TinystoreNce12"}}\n"
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

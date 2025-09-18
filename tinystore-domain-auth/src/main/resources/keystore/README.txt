This folder is reserved for development-only keystore (JKS). Do NOT commit production secrets.
Expected properties (see application.yml):
- tinystore.auth.keystore.location
- tinystore.auth.keystore.password
- tinystore.auth.key.alias
- tinystore.auth.key.password

Generate a local JKS for dev:
keytool -genkeypair -alias auth -keyalg RSA -keysize 2048 -storetype JKS -keystore tinystore-auth.jks -validity 3650 -storepass changeit -keypass changeit -dname "CN=tinystore, OU=dev, O=tinystore, L=City, S=State, C=CN"

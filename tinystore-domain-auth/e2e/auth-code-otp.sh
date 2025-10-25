#!/usr/bin/env bash
set -euo pipefail

# End-to-end test: dynamic register confidential client, OTP login, auth code flow, token exchange, refresh, revoke
# Requirements: curl, jq, openssl

BASE_URL=${BASE_URL:-http://localhost:9000}
REG_TOKEN=${REG_TOKEN:-dev-token}
PHONE=${PHONE:-13900000000}
REDIRECT_URI=${REDIRECT_URI:-https://example.com/callback}

function decode_jwt_claim() {
  local jwt=$1 key=$2
  # decode JWT payload (unsafe, no signature verification)
  local payload=$(echo "$jwt" | cut -d '.' -f2 | tr '_-' '/+' | base64 -d 2>/dev/null || true)
  echo "$payload" | jq -r ".$key"
}

echo "[1] Dynamic register confidential client"
REG_PAYLOAD=$(cat <<JSON
{
  "clientName": "e2e-confidential-client",
  "redirectUris": ["$REDIRECT_URI"],
  "grantTypes": ["authorization_code", "refresh_token"],
  "tokenEndpointAuthMethod": "client_secret_basic",
  "scopes": ["openid", "user.profile"]
}
JSON
)
REG_RESP=$(curl -s -X POST "$BASE_URL/oidc/register" \
  -H "Content-Type: application/json" \
  -H "X-Registration-Token: $REG_TOKEN" \
  -d "$REG_PAYLOAD")
CLIENT_ID=$(echo "$REG_RESP" | jq -r '.clientId')
CLIENT_SECRET=$(echo "$REG_RESP" | jq -r '.clientSecret')
if [[ -z "$CLIENT_ID" || "$CLIENT_ID" == "null" ]]; then echo "Register failed: $REG_RESP"; exit 1; fi

echo "Registered client_id=$CLIENT_ID"

COOKIE_JAR="./e2e-cookies.txt"
rm -f "$COOKIE_JAR"

echo "[2] Send OTP"
curl -s -X POST "$BASE_URL/api/auth/otp/send" \
  -H "Content-Type: application/json" \
  -d "{\"phone\":\"$PHONE\",\"requestId\":\"$(date +%s%N)\",\"ipAddress\":\"127.0.0.1\",\"userAgent\":\"curl-e2e\"}" \
  -o /dev/null

echo "[3] Login with OTP (code=123456)"
# Store session cookie
curl -s -i -c "$COOKIE_JAR" -b "$COOKIE_JAR" -X POST "$BASE_URL/login/otp" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "phone=$PHONE&code=123456" > /dev/null

echo "[4] Authorize (scope=openid user.profile)"
AUTH_RESP_HEADERS=$(curl -s -i -b "$COOKIE_JAR" "$BASE_URL/oauth2/authorize?client_id=$CLIENT_ID&response_type=code&redirect_uri=$(printf %s "$REDIRECT_URI" | sed 's/:/%3A/g;s/\//%2F/g')&scope=openid%20user.profile")
LOCATION=$(echo "$AUTH_RESP_HEADERS" | awk '/^Location:/ {print $2}')
CODE=$(echo "$LOCATION" | sed -E 's/.*[?&]code=([^&[:space:]]+).*/\1/')
if [[ -z "$CODE" ]]; then
  echo "No authorization code redirect, attempting consent approve..."
  # Try to approve consent by posting back to /oauth2/authorize
  # Extract state from response headers/body if present
  STATE=$(echo "$AUTH_RESP_HEADERS" | awk '/^Set-Cookie:/ {print $0}' | sed -n 's/.*state=\([^;]*\).*/\1/p')
  # Fallback: try without state
  CONSENT_FORM=$(cat <<FORM
client_id=$CLIENT_ID&redirect_uri=$(printf %s "$REDIRECT_URI" | sed 's/:/%3A/g;s/\//%2F/g')&scope=openid&scope=user.profile&consent_action=approve${STATE:+&state=$STATE}
FORM
)
  AUTH_RESP_HEADERS=$(curl -s -i -b "$COOKIE_JAR" -X POST "$BASE_URL/oauth2/authorize" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data "$CONSENT_FORM")
  LOCATION=$(echo "$AUTH_RESP_HEADERS" | awk '/^Location:/ {print $2}')
  CODE=$(echo "$LOCATION" | sed -E 's/.*[?&]code=([^&[:space:]]+).*/\1/')
  if [[ -z "$CODE" ]]; then echo "Consent approve failed: $AUTH_RESP_HEADERS"; exit 1; fi
fi

echo "Got authorization code=$CODE"

echo "[5] Exchange code for tokens"
TOKEN_RESP=$(curl -s -u "$CLIENT_ID:$CLIENT_SECRET" -X POST "$BASE_URL/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=$CODE&redirect_uri=$REDIRECT_URI")
ACCESS_TOKEN=$(echo "$TOKEN_RESP" | jq -r '.access_token')
ID_TOKEN=$(echo "$TOKEN_RESP" | jq -r '.id_token')
REFRESH_TOKEN=$(echo "$TOKEN_RESP" | jq -r '.refresh_token')
if [[ -z "$ACCESS_TOKEN" || "$ACCESS_TOKEN" == "null" ]]; then echo "Token exchange failed: $TOKEN_RESP"; exit 1; fi

echo "Access token acquired. Parsing ID token claims"
SUB=$(decode_jwt_claim "$ID_TOKEN" sub)
USER_ID=$(decode_jwt_claim "$ID_TOKEN" user_id)
STATUS=$(decode_jwt_claim "$ID_TOKEN" status)
RTV=$(decode_jwt_claim "$ID_TOKEN" rt_version)
echo "sub=$SUB user_id=$USER_ID status=$STATUS rt_version=$RTV"

echo "[6] Refresh token"
REFRESH_RESP=$(curl -s -u "$CLIENT_ID:$CLIENT_SECRET" -X POST "$BASE_URL/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=refresh_token&refresh_token=$REFRESH_TOKEN")
if [[ $(echo "$REFRESH_RESP" | jq -r '.access_token') == "null" ]]; then echo "Refresh failed: $REFRESH_RESP"; exit 1; fi

echo "[7] Admin revoke tokens (rt_version++)"
REV_RESP=$(curl -s -X POST "$BASE_URL/api/admin/tokens/revoke?user_id=$USER_ID")
# Attempt refresh again (should fail)
REFRESH_RESP2=$(curl -s -u "$CLIENT_ID:$CLIENT_SECRET" -X POST "$BASE_URL/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=refresh_token&refresh_token=$REFRESH_TOKEN")
ERR=$(echo "$REFRESH_RESP2" | jq -r '.error // empty')
if [[ -z "$ERR" ]]; then echo "Expected refresh failure after revoke, got: $REFRESH_RESP2"; exit 1; fi

echo "[OK] E2E completed successfully"
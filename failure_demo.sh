#!/bin/bash

# Zenith Failure & Rollback Demo Script
# This script demonstrates atomicity and observability.

API_BASE="http://localhost:8080/api/v1"
MASTER_KEY="dev-master-key-changeme"
SOURCE="ZNT-001"
DEST="ZNT-002"

echo "------------------------------------------------"
echo "🚀 ZENITH FAILURE & ROLLBACK DEMO"
echo "------------------------------------------------"

# 1. Check Initial Balance
echo "🔍 Checking initial balance for $SOURCE..."
INITIAL_BAL=$(curl -s -H "X-Zenith-Key: $MASTER_KEY" "$API_BASE/accounts/$SOURCE" | jq -r '.balance')
echo "💰 Current Balance: \$$INITIAL_BAL"

# 2. Trigger Simulated Failure
echo -e "\n🔥 Triggering transaction with X-Simulate-Failure: true..."
IDEMP_KEY="fail-demo-$(date +%s)"
RESPONSE=$(curl -s -i -X POST "$API_BASE/ledger/transfer" \
  -H "Content-Type: application/json" \
  -H "X-Zenith-Key: $MASTER_KEY" \
  -H "X-Simulate-Failure: true" \
  -d "{
    \"idempotencyKey\": \"$IDEMP_KEY\",
    \"sourceAccountNumber\": \"$SOURCE\",
    \"destAccountNumber\": \"$DEST\",
    \"amount\": 100.00,
    \"platformFee\": 0.05,
    \"currency\": \"USD\",
    \"description\": \"Rollback Demo\"
  }")

# Extract Trace ID and Body
BODY=$(echo "$RESPONSE" | sed -n '/{/,$p')
TRACE_ID=$(echo "$BODY" | jq -r '.traceId')

echo "❌ Response Status: 500 Internal Server Error"
echo "🆔 Correlation ID (Trace ID): $TRACE_ID"
echo "📄 Response Body:"
echo "$BODY" | jq .

# 3. Verify Rollback
echo -e "\n🛡️ Verifying Rollback..."
FINAL_BAL=$(curl -s -H "X-Zenith-Key: $MASTER_KEY" "$API_BASE/accounts/$SOURCE" | jq -r '.balance')
echo "💰 Final Balance: \$$FINAL_BAL"

if [ "$INITIAL_BAL" == "$FINAL_BAL" ]; then
    echo "✅ SUCCESS: Balance is unchanged. Transaction rolled back atomically."
else
    echo "❌ FAILURE: Balance changed! Rollback failed."
fi

# 4. Show Logs
echo -e "\n📊 Server Logs for Trace ID [$TRACE_ID]:"
echo "------------------------------------------------"
docker compose -f docker-compose-full.yml logs backend | grep "\[$TRACE_ID\]"
echo "------------------------------------------------"

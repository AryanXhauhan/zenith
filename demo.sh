#!/bin/bash

# Zenith Settlement Engine - Demo Script
echo "🚀 Starting Zenith Demo..."

# Security Header
AUTH_HEADER="X-Zenith-Key: dev-master-key-changeme"

# 1. Create Source Account
echo -e "\n1. Creating Source Account (ZNT-001) with $1,000,000..."
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -H "$AUTH_HEADER" \
  -d '{"accountNumber": "ZNT-001", "accountName": "Global User Savings", "balance": 1000000.00, "currency": "USD"}'

# 2. Create Destination Account
echo -e "\n2. Creating Destination Account (ZNT-002)..."
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -H "$AUTH_HEADER" \
  -d '{"accountNumber": "ZNT-002", "accountName": "Merchant Wallet", "balance": 0.00, "currency": "USD"}'

# 3. Perform Double-Entry Transfer
echo -e "\n3. Performing Atomic Transfer of $250.00..."
curl -X POST http://localhost:8080/api/v1/ledger/transfer \
  -H "Content-Type: application/json" \
  -H "$AUTH_HEADER" \
  -d '{
    "idempotencyKey": "txn-'"$(date +%s)"'",
    "sourceAccountNumber": "ZNT-001",
    "destAccountNumber": "ZNT-002",
    "amount": "250.00",
    "platformFee": "0.05",
    "currency": "USD",
    "description": "Payment for order #99"
  }'

# 4. Check Final Balances
echo -e "\n\n4. Verifying Balances..."
echo "--- Account ZNT-001 ---"
curl -s -H "$AUTH_HEADER" http://localhost:8080/api/v1/accounts/ZNT-001
echo -e "\n--- Account ZNT-002 ---"
curl -s -H "$AUTH_HEADER" http://localhost:8080/api/v1/accounts/ZNT-002
echo -e "\n\n✅ Demo Complete. Money moved atomically!"

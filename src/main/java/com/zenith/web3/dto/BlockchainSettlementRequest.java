package com.zenith.web3.dto;

/**
 * Request DTO for blockchain settlement confirmation.
 */
public record BlockchainSettlementRequest(
        String blockchainTxHash,      // 0x... Ethereum transaction hash
        String zenithTransactionId    // UUID of the Zenith internal transaction
) {}

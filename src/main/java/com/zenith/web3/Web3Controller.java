package com.zenith.web3;

import com.zenith.web3.dto.BlockchainSettlementRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST endpoints for Web3 settlement operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/web3")
@RequiredArgsConstructor
public class Web3Controller {

    private final Web3Service web3Service;

    /**
     * POST /api/v1/web3/confirm
     *
     * Called by the client (or a webhook) after a blockchain transaction is mined.
     * Verifies the tx on-chain and updates the Zenith ledger.
     *
     * Request body:
     * {
     *   "blockchainTxHash": "0xabc...",
     *   "zenithTransactionId": "uuid"
     * }
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmSettlement(
            @RequestBody BlockchainSettlementRequest request) {

        log.info("Web3 confirm: zenithTxId={} hash={}", request.zenithTransactionId(), request.blockchainTxHash());

        boolean confirmed = web3Service.confirmAndSettleLedger(
                request.blockchainTxHash(),
                UUID.fromString(request.zenithTransactionId())
        );

        if (confirmed) {
            return ResponseEntity.ok(Map.of(
                    "status",  "CONFIRMED",
                    "message", "Transaction confirmed on-chain and ledger updated.",
                    "txHash",  request.blockchainTxHash()
            ));
        } else {
            return ResponseEntity.accepted().body(Map.of(
                    "status",  "PENDING",
                    "message", "Transaction not yet mined. Retry in a few seconds.",
                    "txHash",  request.blockchainTxHash()
            ));
        }
    }

    /**
     * GET /api/v1/web3/balance/{address}
     * Returns the ETH balance of an Ethereum address.
     */
    @GetMapping("/balance/{address}")
    public ResponseEntity<Map<String, Object>> getBalance(@PathVariable String address) {
        var balanceWei = web3Service.getEthBalance(address);
        return ResponseEntity.ok(Map.of(
                "address",    address,
                "balanceWei", balanceWei.toString(),
                "chainId",    web3Service.getChainId()
        ));
    }

    /**
     * GET /api/v1/web3/escrow
     * Returns the deployed escrow contract address.
     */
    @GetMapping("/escrow")
    public ResponseEntity<Map<String, String>> getEscrowInfo() {
        return ResponseEntity.ok(Map.of(
                "contractAddress", web3Service.getEscrowContractAddress()
        ));
    }
}

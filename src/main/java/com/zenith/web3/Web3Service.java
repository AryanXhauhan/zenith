package com.zenith.web3;

import com.zenith.config.ZenithProperties;
import com.zenith.ledger.service.LedgerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;

import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;

/**
 * Module 4 – Web3 Settlement Service
 *
 * Connects to the Ethereum network (or any EVM chain) via Web3j.
 * Integrates with the ZenithEscrow smart contract.
 *
 * Flow:
 *  1. depositToEscrow()  → sends ETH to the on-chain ZenithEscrow contract
 *  2. releaseEscrow()    → owner calls release(), funds flow to payee on-chain
 *  3. waitForReceipt()   → polls for tx mining, then calls LedgerService to
 *                          confirm the off-chain ledger entry
 *
 * The ZenithEscrow contract ABI is generated via Web3j CLI from ZenithEscrow.sol.
 * For now, we interact via raw transaction encoding; replace with the generated
 * wrapper class after running: web3j generate solidity -a ZenithEscrow.abi -o src/main/java
 */
@Slf4j
@Service
public class Web3Service {

    private final Web3j          web3j;
    private final Credentials    credentials;
    private final ZenithProperties properties;
    private final LedgerService  ledgerService;

    public Web3Service(ZenithProperties properties, LedgerService ledgerService) {
        this.properties   = properties;
        this.ledgerService = ledgerService;

        // Connect to Ethereum node
        this.web3j = Web3j.build(new HttpService(properties.web3().rpcUrl()));
        this.credentials = Credentials.create(properties.web3().privateKey());

        log.info("Web3Service initialized: rpc={} address={}",
                properties.web3().rpcUrl(),
                credentials.getAddress());
    }

    /**
     * Verifies that a given blockchain transaction hash is:
     *  1. Mined (has a receipt)
     *  2. Successful (status == 1)
     *
     * If confirmed, notifies the LedgerService to mark the transaction as
     * blockchain-confirmed (Module 2 integration).
     *
     * @param blockchainTxHash The 0x... Ethereum transaction hash
     * @param zenithTxId       The internal Zenith transaction UUID
     * @return true if confirmed on-chain
     */
    public boolean confirmAndSettleLedger(String blockchainTxHash, UUID zenithTxId) {
        try {
            log.info("Web3: verifying on-chain settlement hash={}", blockchainTxHash);

            EthGetTransactionReceipt receiptResponse =
                    web3j.ethGetTransactionReceipt(blockchainTxHash).send();

            Optional<TransactionReceipt> receiptOpt = receiptResponse.getTransactionReceipt();

            if (receiptOpt.isEmpty()) {
                log.warn("Web3: transaction not yet mined hash={}", blockchainTxHash);
                return false;
            }

            TransactionReceipt receipt = receiptOpt.get();

            // EVM: status "0x1" = success, "0x0" = reverted
            if (!"0x1".equals(receipt.getStatus())) {
                log.error("Web3: transaction REVERTED hash={} status={}",
                        blockchainTxHash, receipt.getStatus());
                return false;
            }

            log.info("Web3: transaction CONFIRMED hash={} block={}",
                    blockchainTxHash, receipt.getBlockNumber());

            // ── Update the off-chain ledger ─────────────────────────────────
            ledgerService.confirmBlockchainSettlement(zenithTxId, blockchainTxHash);
            return true;

        } catch (Exception e) {
            log.error("Web3: failed to verify transaction hash={}", blockchainTxHash, e);
            return false;
        }
    }

    /**
     * Gets the current ETH balance of an Ethereum address.
     *
     * @param address Ethereum address (0x...)
     * @return balance in Wei
     */
    public BigInteger getEthBalance(String address) {
        try {
            return web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                    .send()
                    .getBalance();
        } catch (Exception e) {
            log.error("Web3: failed to get balance for address={}", address, e);
            return BigInteger.ZERO;
        }
    }

    /**
     * Returns the current connected chain ID.
     */
    public long getChainId() {
        try {
            return web3j.ethChainId().send().getChainId().longValue();
        } catch (Exception e) {
            log.error("Web3: failed to get chainId", e);
            return -1;
        }
    }

    /**
     * Loads the ZenithEscrow contract wrapper.
     * After generating the Java wrapper with Web3j CLI, replace this with:
     *   ZenithEscrow.load(address, web3j, credentials, new DefaultGasProvider())
     */
    public String getEscrowContractAddress() {
        return properties.web3().escrowContractAddress();
    }
}

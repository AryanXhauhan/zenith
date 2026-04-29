package com.zenith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Zenith Settlement Engine – Entry Point
 *
 * A high-performance, double-entry FinTech settlement platform with:
 *   • Machine-to-machine API Gateway with Redis-backed rate limiting
 *   • ACID-compliant double-entry ledger on PostgreSQL
 *   • Idempotency service preventing double-charges
 *   • Web3/Ethereum settlement via smart contract escrow
 *   • Kubernetes-ready with actuator health probes
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ZenithApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZenithApplication.class, args);
    }
}

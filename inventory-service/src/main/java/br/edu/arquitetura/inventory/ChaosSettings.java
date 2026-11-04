package br.edu.arquitetura.inventory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Injeção de falhas em runtime para demonstrar timeouts, retries e circuit breaker. */
@Component
public class ChaosSettings {

    private final AtomicLong latencyMs;
    private final AtomicInteger failurePercent;

    public ChaosSettings(@Value("${chaos.latency-ms:0}") long latencyMs,
                         @Value("${chaos.failure-percent:0}") int failurePercent) {
        this.latencyMs = new AtomicLong(latencyMs);
        this.failurePercent = new AtomicInteger(failurePercent);
    }

    public long latencyMs() { return latencyMs.get(); }

    public int failurePercent() { return failurePercent.get(); }

    public void update(long latency, int failure) {
        latencyMs.set(Math.max(0, latency));
        failurePercent.set(Math.min(100, Math.max(0, failure)));
    }
}

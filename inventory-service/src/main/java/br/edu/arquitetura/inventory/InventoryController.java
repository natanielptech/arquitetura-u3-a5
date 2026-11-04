package br.edu.arquitetura.inventory;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    private final ChaosSettings chaos;
    // Estoque em memória: cada réplica tem o seu (tradeoff: estado local não escala; use um banco compartilhado em produção).
    private final Map<String, Integer> stock = new ConcurrentHashMap<>(Map.of(
            "BOOK-1", 100, "BOOK-2", 5, "BOOK-3", 0));

    public InventoryController(ChaosSettings chaos) {
        this.chaos = chaos;
    }

    @GetMapping("/inventory/{sku}")
    public Map<String, Object> get(@PathVariable String sku) throws Exception {
        applyChaos();
        Integer available = stock.get(sku);
        if (available == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SKU desconhecido: " + sku);
        }
        log.info("consulta de estoque sku={} available={}", sku, available);
        return Map.of("sku", sku, "available", available, "pod", InetAddress.getLocalHost().getHostName());
    }

    @PostMapping("/chaos")
    public Map<String, Object> chaos(@RequestParam(defaultValue = "0") long latencyMs,
                                     @RequestParam(defaultValue = "0") int failurePercent) {
        chaos.update(latencyMs, failurePercent);
        return Map.of("latencyMs", chaos.latencyMs(), "failurePercent", chaos.failurePercent());
    }

    /** Consome CPU por N ms: serve para disparar o HPA. */
    @GetMapping("/load/cpu")
    public Map<String, Object> cpu(@RequestParam(defaultValue = "200") long millis) {
        long end = System.nanoTime() + millis * 1_000_000;
        double x = 0;
        while (System.nanoTime() < end) {
            x += Math.sqrt(ThreadLocalRandom.current().nextDouble());
        }
        return Map.of("burnedMs", millis, "checksum", x);
    }

    private void applyChaos() throws InterruptedException {
        if (chaos.latencyMs() > 0) {
            Thread.sleep(chaos.latencyMs());
        }
        if (ThreadLocalRandom.current().nextInt(100) < chaos.failurePercent()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "falha injetada");
        }
    }
}

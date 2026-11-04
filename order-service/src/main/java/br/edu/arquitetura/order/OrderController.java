package br.edu.arquitetura.order;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    public record CreateOrder(String sku, int quantity) {}

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping("/orders")
    public ResponseEntity<Order> create(@RequestBody CreateOrder req,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        Order order = service.create(req.sku(), req.quantity(), key);
        // 202 quando ainda não validamos o estoque (consistência eventual)
        HttpStatus status = order.status() == Order.Status.PENDING_VALIDATION ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(order);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> get(@PathVariable String id) {
        return service.find(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /** Mostra qual pod respondeu: útil para ver o balanceamento do Service. */
    @GetMapping("/whoami")
    public Map<String, Object> whoami() throws Exception {
        return Map.of("pod", InetAddress.getLocalHost().getHostName(),
                "cpus", Runtime.getRuntime().availableProcessors(),
                "maxHeapMb", Runtime.getRuntime().maxMemory() / 1024 / 1024);
    }

    /** Consome CPU por N ms: serve para disparar o HPA / demonstrar saturação. */
    @GetMapping("/load/cpu")
    public Map<String, Object> cpu(@RequestParam(defaultValue = "200") long millis) {
        long end = System.nanoTime() + millis * 1_000_000;
        double x = 0;
        while (System.nanoTime() < end) {
            x += Math.sqrt(ThreadLocalRandom.current().nextDouble());
        }
        return Map.of("burnedMs", millis, "checksum", x);
    }
}

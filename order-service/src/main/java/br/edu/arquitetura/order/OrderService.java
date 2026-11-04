package br.edu.arquitetura.order;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderService {

    private final StockChecker inventory;
    private final MeterRegistry metrics;
    // Em memória por réplica. Em produção: banco compartilhado (senão a escala horizontal quebra o GET /orders/{id}).
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, String> idempotency = new ConcurrentHashMap<>();

    public OrderService(StockChecker inventory, MeterRegistry metrics) {
        this.inventory = inventory;
        this.metrics = metrics;
    }

    /** Idempotente: repetir a mesma chave (ex.: retry do cliente) devolve o mesmo pedido. */
    public Order create(String sku, int quantity, String idempotencyKey) {
        if (idempotencyKey != null) {
            String existing = idempotency.get(idempotencyKey);
            if (existing != null) {
                return orders.get(existing);
            }
        }
        var stock = inventory.check(sku);
        Order.Status status = stock.degraded() ? Order.Status.PENDING_VALIDATION
                : stock.available() >= quantity ? Order.Status.CONFIRMED : Order.Status.REJECTED;
        Order order = new Order(UUID.randomUUID().toString(), sku, quantity, status);
        orders.put(order.id(), order);
        if (idempotencyKey != null) {
            idempotency.put(idempotencyKey, order.id());
        }
        metrics.counter("orders.created", "status", status.name()).increment();
        return order;
    }

    public Optional<Order> find(String id) {
        return Optional.ofNullable(orders.get(id));
    }
}

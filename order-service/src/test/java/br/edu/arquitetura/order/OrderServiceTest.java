package br.edu.arquitetura.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    AtomicInteger calls = new AtomicInteger();

    OrderService serviceWith(Integer available) {
        return new OrderService(sku -> {
            calls.incrementAndGet();
            return new StockChecker.Stock(available);
        }, new SimpleMeterRegistry());
    }

    @Test
    void confirmsWhenStockIsEnough() {
        assertEquals(Order.Status.CONFIRMED, serviceWith(10).create("A", 2, null).status());
    }

    @Test
    void rejectsWhenStockIsLow() {
        assertEquals(Order.Status.REJECTED, serviceWith(1).create("A", 2, null).status());
    }

    @Test
    void degradesToPendingWhenInventoryIsDown() {
        assertEquals(Order.Status.PENDING_VALIDATION, serviceWith(null).create("A", 2, null).status());
    }

    @Test
    void sameIdempotencyKeyReturnsSameOrder() {
        OrderService service = serviceWith(10);
        Order first = service.create("A", 1, "k1");
        assertSame(first, service.create("A", 1, "k1"));
        assertEquals(1, calls.get());
    }
}

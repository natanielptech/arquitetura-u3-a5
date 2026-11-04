package br.edu.arquitetura.order;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Chamada remota protegida por: timeout -> circuit breaker -> retry (com backoff) -> fallback.
 * A ordem dos aspectos do Resilience4j é Retry( CircuitBreaker( chamada ) ).
 */
@Component
public class InventoryClient implements StockChecker {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);

    private final RestClient client;

    public InventoryClient(RestClient.Builder builder,
                           @Value("${inventory.url}") String baseUrl,
                           @Value("${inventory.timeout-ms:800}") long timeoutMs) {
        ClientHttpRequestFactory factory = ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(Duration.ofMillis(timeoutMs))
                        .withReadTimeout(Duration.ofMillis(timeoutMs)));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    @Retry(name = "inventory", fallbackMethod = "fallback")
    @CircuitBreaker(name = "inventory")
    public Stock check(String sku) {
        try {
            var body = client.get().uri("/inventory/{sku}", sku).retrieve().body(InventoryResponse.class);
            return new Stock(body == null ? 0 : body.available());
        } catch (HttpClientErrorException.NotFound e) {
            return new Stock(0); // erro de negócio: não deve abrir o circuito nem ser repetido
        }
    }

    Stock fallback(String sku, Throwable t) {
        log.warn("inventory indisponível ({}), degradando sku={}", t.getClass().getSimpleName(), sku);
        return new Stock(null);
    }

    record InventoryResponse(String sku, int available) {}
}

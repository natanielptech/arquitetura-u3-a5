package br.edu.arquitetura.order;

public record Order(String id, String sku, int quantity, Status status) {

    public enum Status {
        /** Estoque confirmado. */
        CONFIRMED,
        /** Estoque insuficiente ou SKU inexistente. */
        REJECTED,
        /** Inventory indisponível: aceitamos o pedido e validamos depois (consistência eventual). */
        PENDING_VALIDATION
    }
}

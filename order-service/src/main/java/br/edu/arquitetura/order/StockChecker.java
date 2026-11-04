package br.edu.arquitetura.order;

public interface StockChecker {

    /** available == null significa "não sei" (degradado). */
    record Stock(Integer available) {
        public boolean degraded() { return available == null; }
    }

    Stock check(String sku);
}

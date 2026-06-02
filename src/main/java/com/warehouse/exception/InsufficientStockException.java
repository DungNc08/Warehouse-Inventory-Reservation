package com.warehouse.exception;

public class InsufficientStockException extends BusinessException {

    public InsufficientStockException(String sku, int available, int requested) {
        super(
                "INSUFFICIENT_STOCK",
                "SKU " + sku + " has only " + available + " units available, " + requested + " were requested"
        );
    }
}

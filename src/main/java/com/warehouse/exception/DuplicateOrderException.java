package com.warehouse.exception;

public class DuplicateOrderException extends BusinessException {

    public DuplicateOrderException(String orderId) {
        super("DUPLICATE_ORDER", "A reservation already exists for order " + orderId);
    }
}

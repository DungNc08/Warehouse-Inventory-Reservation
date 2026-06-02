package com.warehouse.exception;

public class InvalidStateTransitionException extends BusinessException {

    public InvalidStateTransitionException(String code, String message) {
        super(code, message);
    }
}

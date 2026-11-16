package com.banking.exception;

public final class PersistenceException extends BankingException {
    public PersistenceException(String message) { super(message); }
    public PersistenceException(String message, Throwable cause) { super(message, cause); }
}

package com.banking.exception;

public final class InsufficientBalanceException extends BankingException {
    public InsufficientBalanceException() { super("Insufficient balance: the account's available funds would be exceeded."); }
}

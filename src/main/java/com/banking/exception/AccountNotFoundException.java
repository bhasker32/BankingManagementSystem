package com.banking.exception;

public final class AccountNotFoundException extends BankingException {
    public AccountNotFoundException(String number) { super("Account not found: " + number); }
}

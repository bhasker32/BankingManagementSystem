package com.banking.model;

import com.banking.exception.ValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class SavingsAccount extends Account {
    public static final BigDecimal ANNUAL_INTEREST_RATE = new BigDecimal("0.04");

    public SavingsAccount(String number, Customer customer, BigDecimal balance) { this(number, customer, balance, List.of()); }
    private SavingsAccount(String number, Customer customer, BigDecimal balance, List<Transaction> history) {
        super(number, customer, balance, AccountType.SAVINGS, history);
        if (balance.signum() < 0) { throw new ValidationException("Savings balance cannot be negative."); }
    }
    @Override public BigDecimal calculateInterest() { return getBalance().multiply(ANNUAL_INTEREST_RATE).setScale(2, RoundingMode.HALF_EVEN); }
    @Override public BigDecimal getAvailableFunds() { return getBalance(); }
    @Override protected Account copy(BigDecimal balance, List<Transaction> history) {
        return new SavingsAccount(getAccountNumber(), getCustomer(), balance, history);
    }
}

package com.banking.model;

import com.banking.exception.ValidationException;
import java.math.BigDecimal;
import java.util.List;

public final class CurrentAccount extends Account {
    // Deliberately zero for this exercise: normal spending cannot exceed the deposited balance.
    public static final BigDecimal OVERDRAFT_LIMIT = new BigDecimal("0.00");

    public CurrentAccount(String number, Customer customer, BigDecimal balance) { this(number, customer, balance, List.of()); }
    private CurrentAccount(String number, Customer customer, BigDecimal balance, List<Transaction> history) {
        super(number, customer, balance, AccountType.CURRENT, history);
        if (balance.compareTo(OVERDRAFT_LIMIT.negate()) < 0) { throw new ValidationException("Current balance exceeds the overdraft limit."); }
    }
    public BigDecimal getOverdraftLimit() { return OVERDRAFT_LIMIT; }
    @Override public BigDecimal calculateInterest() { return new BigDecimal("0.00"); }
    @Override public BigDecimal getAvailableFunds() { return getBalance().add(OVERDRAFT_LIMIT); }
    @Override protected Account copy(BigDecimal balance, List<Transaction> history) {
        return new CurrentAccount(getAccountNumber(), getCustomer(), balance, history);
    }
}

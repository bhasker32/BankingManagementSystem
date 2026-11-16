package com.banking.model;

import com.banking.exception.InsufficientBalanceException;
import com.banking.exception.ValidationException;
import com.banking.util.InputValidator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable account snapshot. Operations return candidates; only the service commits them. */
public abstract sealed class Account permits SavingsAccount, CurrentAccount {
    private final String accountNumber;
    private final Customer customer;
    private final BigDecimal balance;
    private final AccountType accountType;
    private final ArrayList<Transaction> transactionHistory;

    protected Account(String number, Customer customer, BigDecimal balance, AccountType type, List<Transaction> history) {
        this.accountNumber = InputValidator.identifier(number, "ACC");
        this.customer = Objects.requireNonNull(customer, "Customer");
        this.balance = InputValidator.balance(balance);
        this.accountType = type;
        this.transactionHistory = new ArrayList<>(history);
    }

    public final String getAccountNumber() { return accountNumber; }
    public final Customer getCustomer() { return customer; }
    public final BigDecimal getBalance() { return balance; }
    public final AccountType getAccountType() { return accountType; }
    public final List<Transaction> getTransactionHistory() { return List.copyOf(transactionHistory); }
    public abstract BigDecimal calculateInterest();
    public abstract BigDecimal getAvailableFunds();
    protected abstract Account copy(BigDecimal balance, List<Transaction> history);

    public final Account deposit(BigDecimal amount) {
        return copy(InputValidator.balance(balance.add(InputValidator.amount(amount))), transactionHistory);
    }

    public final Account withdraw(BigDecimal amount) {
        BigDecimal valid = InputValidator.amount(amount);
        if (valid.compareTo(getAvailableFunds()) > 0) { throw new InsufficientBalanceException(); }
        return copy(InputValidator.balance(balance.subtract(valid)), transactionHistory);
    }

    public final Account withTransaction(Transaction transaction) {
        if (!accountNumber.equals(transaction.accountNumber()) || balance.compareTo(transaction.balanceAfterTransaction()) != 0) {
            throw new ValidationException("Transaction does not match account state.");
        }
        ArrayList<Transaction> updated = new ArrayList<>(transactionHistory);
        updated.add(transaction);
        return copy(balance, updated);
    }

    @Override public String toString() {
        return accountNumber + " | " + customer.getName() + " | " + accountType + " | INR " + balance;
    }
}

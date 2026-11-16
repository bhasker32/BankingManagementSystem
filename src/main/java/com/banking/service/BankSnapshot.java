package com.banking.service;

import com.banking.model.Account;
import com.banking.model.Transaction;
import java.math.BigDecimal;
import java.util.List;

/** Consistent, immutable data for presentation. Transfer operations are counted once. */
public record BankSnapshot(List<Account> accounts, List<Transaction> transactions, int savingsAccounts,
        int currentAccounts, BigDecimal totalBalance, long transactionCount) {
    public BankSnapshot { accounts = List.copyOf(accounts); transactions = List.copyOf(transactions); }
}

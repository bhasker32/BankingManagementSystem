package com.banking.service;

import com.banking.model.*;
import com.banking.persistence.BankStore;
import com.banking.util.AccountNumberGenerator;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public final class AccountService {
    private final BankStore store;
    private final AccountNumberGenerator numbers = new AccountNumberGenerator();

    public AccountService(BankStore store) { this.store = Objects.requireNonNull(store); }

    public Account createAccount(String name, String phone, String email, AccountType type) {
        synchronized (store) {
            if (type == null) { throw new com.banking.exception.ValidationException("Account type is required."); }
            LinkedHashMap<String, Account> candidate = new LinkedHashMap<>();
            store.accounts().forEach(a -> candidate.put(a.getAccountNumber(), a));
            String number = numbers.next(candidate.keySet());
            Customer customer = new Customer("CUST" + number.substring(3), name, phone, email);
            Account account = switch (type) {
                case SAVINGS -> new SavingsAccount(number, customer, BigDecimal.ZERO);
                case CURRENT -> new CurrentAccount(number, customer, BigDecimal.ZERO);
            };
            candidate.put(number, account);
            store.commit(candidate, store.transactions());
            return store.account(number);
        }
    }

    public Account findAccount(String number) { synchronized (store) { return store.account(number); } }
    public List<Account> listAccounts() { synchronized (store) { return store.accounts(); } }
}

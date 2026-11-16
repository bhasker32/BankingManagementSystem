package com.banking.service;

import com.banking.persistence.BankStore;
import com.banking.model.*;
import java.math.BigDecimal;
import java.util.Comparator;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Application lifecycle and access to the two business services sharing one committed state. */
public final class BankService implements AutoCloseable {
    private final BankStore store;
    private final AccountService accounts;
    private final TransactionService transactions;

    public BankService(Path dataDirectory, Consumer<String> warnings) {
        store = new BankStore(dataDirectory, warnings);
        accounts = new AccountService(store);
        transactions = new TransactionService(store);
    }
    public AccountService accounts() { return accounts; }
    public TransactionService transactions() { return transactions; }
    public BankSnapshot snapshot() {
        synchronized (store) {
            var list = store.accounts();
            var ledger = store.transactions().stream().sorted(Comparator.comparing(Transaction::timestamp).reversed()
                    .thenComparing(Transaction::transactionId, Comparator.reverseOrder())).toList();
            int savings = (int) list.stream().filter(a -> a.getAccountType() == AccountType.SAVINGS).count();
            BigDecimal total = list.stream().map(Account::getBalance).reduce(new BigDecimal("0.00"), BigDecimal::add);
            return new BankSnapshot(list, ledger, savings, list.size() - savings, total,
                    ledger.stream().map(Transaction::transactionId).distinct().count());
        }
    }
    public BankSnapshot refresh() { synchronized (store) { store.reload(); return snapshot(); } }
    @Override public void close() { synchronized (store) { store.close(); } }
}

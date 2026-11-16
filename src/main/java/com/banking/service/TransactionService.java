package com.banking.service;

import com.banking.exception.ValidationException;
import com.banking.model.*;
import com.banking.persistence.BankStore;
import com.banking.util.InputValidator;
import com.banking.util.TransactionIdGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

public final class TransactionService {
    private final BankStore store;
    private final Clock clock;
    private final TransactionIdGenerator ids = new TransactionIdGenerator();

    public TransactionService(BankStore store) { this(store, Clock.systemDefaultZone()); }
    public TransactionService(BankStore store, Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
    }

    public Transaction deposit(String number, BigDecimal amount) { return change(number, amount, TransactionType.DEPOSIT); }
    public Transaction withdraw(String number, BigDecimal amount) { return change(number, amount, TransactionType.WITHDRAWAL); }

    private Transaction change(String number, BigDecimal amount, TransactionType type) {
        synchronized (store) {
            Account account = store.account(number);
            BigDecimal valid = InputValidator.amount(amount);
            Account changed = type == TransactionType.DEPOSIT ? account.deposit(valid) : account.withdraw(valid);
            Transaction transaction = entry(nextId(), type, changed, "", valid, LocalDateTime.now(clock),
                    type == TransactionType.DEPOSIT ? "Cash deposit" : "Cash withdrawal");
            commit(List.of(changed.withTransaction(transaction)), List.of(transaction));
            return transaction;
        }
    }

    public Transaction transfer(String sourceNumber, String destinationNumber, BigDecimal amount) {
        return transfer(sourceNumber, destinationNumber, amount, "");
    }

    public Transaction transfer(String sourceNumber, String destinationNumber, BigDecimal amount, String description) {
        synchronized (store) {
            String note = validateNote(description);
            Account source = store.account(sourceNumber);
            Account destination = store.account(destinationNumber);
            if (sourceNumber.equals(destinationNumber)) { throw new ValidationException("Source and destination must be different accounts."); }
            BigDecimal valid = InputValidator.amount(amount);
            // Build BOTH candidates before generating an ID or touching the committed map.
            Account debited = source.withdraw(valid);
            Account credited = destination.deposit(valid);
            String id = nextId();
            LocalDateTime timestamp = LocalDateTime.now(clock);
            Transaction out = entry(id, TransactionType.TRANSFER, debited, destinationNumber, valid, timestamp, "Transfer out" + note);
            Transaction in = entry(id, TransactionType.TRANSFER, credited, sourceNumber, valid, timestamp, "Transfer in" + note);
            commit(List.of(debited.withTransaction(out), credited.withTransaction(in)), List.of(out, in));
            return out;
        }
    }

    public TransactionPreview preview(TransactionType type, String sourceNumber, String destinationNumber, BigDecimal amount) {
        synchronized (store) {
            Objects.requireNonNull(type);
            Account source = store.account(sourceNumber);
            Account destination = null;
            if (type == TransactionType.TRANSFER) {
                destination = store.account(destinationNumber);
                if (sourceNumber.equals(destinationNumber)) { throw new ValidationException("Source and destination accounts must be different."); }
            }
            BigDecimal valid = InputValidator.amount(amount);
            Account changed = type == TransactionType.DEPOSIT ? source.deposit(valid) : source.withdraw(valid);
            BigDecimal destinationBalance = null;
            if (type == TransactionType.TRANSFER) {
                destinationBalance = destination.deposit(valid).getBalance();
            }
            return new TransactionPreview(source.getBalance(), changed.getBalance(), destinationBalance);
        }
    }

    private String validateNote(String value) {
        if (value == null || value.isBlank()) { return ""; }
        return ": " + InputValidator.text(value, "Transfer description", 140);
    }

    public List<Transaction> history(String number) { synchronized (store) { return store.account(number).getTransactionHistory(); } }

    private String nextId() { return ids.next(store.transactions().stream().map(Transaction::transactionId).toList()); }

    private Transaction entry(String id, TransactionType type, Account account, String related, BigDecimal amount,
            LocalDateTime timestamp, String description) {
        return new Transaction(id, type, account.getAccountNumber(), related, amount, timestamp, TransactionStatus.SUCCESS,
                description, account.getBalance());
    }

    private void commit(List<Account> changed, List<Transaction> additions) {
        LinkedHashMap<String, Account> candidate = new LinkedHashMap<>();
        store.accounts().forEach(a -> candidate.put(a.getAccountNumber(), a));
        changed.forEach(a -> candidate.put(a.getAccountNumber(), a));
        List<Transaction> ledger = new ArrayList<>(store.transactions());
        ledger.addAll(additions);
        store.commit(candidate, ledger);
    }
}

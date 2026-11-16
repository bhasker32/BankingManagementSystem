package com.banking.persistence;

import com.banking.exception.AccountNotFoundException;
import com.banking.exception.PersistenceException;
import com.banking.model.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import static java.nio.file.StandardOpenOption.*;

/** Shared committed state and a small redo journal coordinating the two text files. */
public class BankStore implements AutoCloseable {
    private final Path directory;
    private final Consumer<String> warnings;
    private final AccountFileHandler accountFiles = new AccountFileHandler();
    private final TransactionFileHandler transactionFiles = new TransactionFileHandler();
    private FileChannel lockChannel;
    private FileLock lock;
    private LinkedHashMap<String, Account> accounts = new LinkedHashMap<>();
    private List<Transaction> transactions = List.of();
    private boolean closed;
    private boolean refreshFailed;

    public BankStore(Path directory, Consumer<String> warnings) {
        this.directory = directory.toAbsolutePath().normalize();
        this.warnings = Objects.requireNonNull(warnings);
        try {
            Files.createDirectories(this.directory);
            lockChannel = FileChannel.open(this.directory.resolve("bank.lock"), CREATE, WRITE);
            lock = lockChannel.tryLock();
            if (lock == null) { throw new PersistenceException("Data directory is already in use by another application."); }
            recover();
            Path accountPath = this.directory.resolve("accounts.txt");
            Path transactionPath = this.directory.resolve("transactions.txt");
            if (!Files.exists(accountPath)) { Files.createFile(accountPath); }
            if (!Files.exists(transactionPath)) { Files.createFile(transactionPath); }
            accounts = accountFiles.loadAccounts(Files.readAllLines(accountPath, StandardCharsets.UTF_8));
            transactions = List.copyOf(transactionFiles.loadTransactions(Files.readAllLines(transactionPath, StandardCharsets.UTF_8)));
            accounts = validateAndAttach(accounts, transactions);
        } catch (IOException | RuntimeException e) {
            try { close(); } catch (RuntimeException closeFailure) { e.addSuppressed(closeFailure); }
            if (e instanceof PersistenceException p) { throw p; }
            String message = e instanceof OverlappingFileLockException ? "Data directory is already in use." : "Cannot load bank data. Original files preserved.";
            throw new PersistenceException(message, e);
        }
    }

    public Account account(String number) {
        ensureOpen();
        Account account = accounts.get(number);
        if (account == null) { throw new AccountNotFoundException(number); }
        return account;
    }

    public List<Account> accounts() { ensureOpen(); return List.copyOf(accounts.values()); }
    public List<Transaction> transactions() { ensureOpen(); return transactions; }

    /** Publish only a fully validated reload, keeping the current snapshot on failure. */
    public void reload() {
        ensureOpen();
        try {
            recover();
            var loadedAccounts = accountFiles.loadAccounts(Files.readAllLines(directory.resolve("accounts.txt"), StandardCharsets.UTF_8));
            var loadedTransactions = List.copyOf(transactionFiles.loadTransactions(Files.readAllLines(directory.resolve("transactions.txt"), StandardCharsets.UTF_8)));
            var checked = validateAndAttach(loadedAccounts, loadedTransactions);
            if (!checked.keySet().containsAll(accounts.keySet()) || loadedTransactions.size() < transactions.size()
                    || !loadedTransactions.subList(0, transactions.size()).equals(transactions)) {
                throw new PersistenceException("Saved data is older than the open session. Restore the complete data files before refreshing.");
            }
            accounts = checked;
            transactions = loadedTransactions;
            refreshFailed = false;
        } catch (IOException | RuntimeException e) {
            refreshFailed = true;
            throw new PersistenceException("Refresh failed. Last loaded data is still visible; changes are blocked until a successful refresh. " + e.getMessage(), e);
        }
    }

    /** Caller holds this store's monitor throughout validation, planning and commit. */
    public void commit(LinkedHashMap<String, Account> candidate, List<Transaction> ledger) {
        ensureOpen();
        if (refreshFailed) { throw new PersistenceException("Resolve the data file error and refresh before making changes."); }
        LinkedHashMap<String, Account> checked = validateAndAttach(candidate, ledger);
        List<Transaction> committedLedger = List.copyOf(ledger);
        List<String> journal = new ArrayList<>();
        journal.add("SMART-BANK-V1");
        accountFiles.encode(checked.values()).forEach(line -> journal.add("A|" + line));
        transactionFiles.encode(committedLedger).forEach(line -> journal.add("T|" + line));
        journal.add("END");
        try {
            recover(); // Finish any previously committed snapshot before starting the next one.
            writeAtomically(directory.resolve("pending.commit"), journal);
        } catch (IOException e) {
            throw new PersistenceException("Unable to persist operation; balances and transaction IDs are unchanged.", e);
        }
        // The atomic journal rename is the commit point: the complete state is now recoverable.
        accounts = checked;
        transactions = committedLedger;
        try { recover(); }
        catch (IOException e) {
            warnings.accept("Operation saved in recovery journal. Snapshot files will be repaired on the next operation or restart.");
        }
    }

    private void recover() throws IOException {
        Path pending = directory.resolve("pending.commit");
        if (!Files.exists(pending)) { return; }
        List<String> journal = Files.readAllLines(pending, StandardCharsets.UTF_8);
        if (journal.size() < 2 || !journal.get(0).equals("SMART-BANK-V1") || !journal.get(journal.size() - 1).equals("END")) {
            throw new PersistenceException("Malformed recovery journal. Data preserved; manual inspection required.");
        }
        List<String> accountLines = new ArrayList<>();
        List<String> transactionLines = new ArrayList<>();
        boolean inTransactions = false;
        for (String line : journal.subList(1, journal.size() - 1)) {
            if (line.startsWith("A|") && !inTransactions) { accountLines.add(line.substring(2)); }
            else if (line.startsWith("T|")) { inTransactions = true; transactionLines.add(line.substring(2)); }
            else { throw new PersistenceException("Malformed recovery journal entry. Data preserved."); }
        }
        validateAndAttach(accountFiles.loadAccounts(accountLines), transactionFiles.loadTransactions(transactionLines));
        writeAtomically(directory.resolve("accounts.txt"), accountLines);
        writeAtomically(directory.resolve("transactions.txt"), transactionLines);
        Files.delete(pending);
    }

    /** Protected to allow deterministic I/O failure injection in tests, without changing production data. */
    protected void writeAtomically(Path destination, List<String> lines) throws IOException {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        byte[] bytes = (String.join("\n", lines) + (lines.isEmpty() ? "" : "\n")).getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(temporary, CREATE, TRUNCATE_EXISTING, WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) { channel.write(buffer); }
            channel.force(true);
        }
        // No non-atomic fallback: unsupported filesystems must reject the operation safely.
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private LinkedHashMap<String, Account> validateAndAttach(Map<String, Account> saved, List<Transaction> ledger) {
        try {
            LinkedHashMap<String, Account> replay = new LinkedHashMap<>();
            Set<String> customerIds = new HashSet<>();
            for (Account a : saved.values()) {
                if (!customerIds.add(a.getCustomer().getCustomerId())) { throw new IllegalArgumentException("Duplicate customer ID"); }
                Account zero = switch (a.getAccountType()) {
                    case SAVINGS -> new SavingsAccount(a.getAccountNumber(), a.getCustomer(), BigDecimal.ZERO);
                    case CURRENT -> new CurrentAccount(a.getAccountNumber(), a.getCustomer(), BigDecimal.ZERO);
                };
                replay.put(a.getAccountNumber(), zero);
            }
            long previousId = 100000;
            for (int i = 0; i < ledger.size(); i++) {
                Transaction t = ledger.get(i);
                long sequence = Long.parseLong(t.transactionId().substring(3));
                if (sequence <= previousId || t.status() != TransactionStatus.SUCCESS) { throw new IllegalArgumentException("Duplicate/out-of-order ID or non-success entry"); }
                previousId = sequence;
                if (t.transactionType() == TransactionType.TRANSFER) {
                    if (!t.isOutgoingTransfer() || ++i >= ledger.size()) { throw new IllegalArgumentException("Missing transfer pair"); }
                    Transaction incoming = ledger.get(i);
                    if (!incoming.transactionId().equals(t.transactionId()) || incoming.transactionType() != TransactionType.TRANSFER
                            || incoming.status() != TransactionStatus.SUCCESS || !incoming.isIncomingTransfer()
                            || !t.transferNote().equals(incoming.transferNote())
                            || !t.relatedAccountNumber().equals(incoming.accountNumber())
                            || !t.accountNumber().equals(incoming.relatedAccountNumber())
                            || t.amount().compareTo(incoming.amount()) != 0 || !t.timestamp().equals(incoming.timestamp())) {
                        throw new IllegalArgumentException("Inconsistent transfer pair");
                    }
                    replayEntry(replay, t, false);
                    replayEntry(replay, incoming, true);
                } else { replayEntry(replay, t, t.transactionType() == TransactionType.DEPOSIT); }
            }
            for (Account a : saved.values()) {
                if (a.getBalance().compareTo(replay.get(a.getAccountNumber()).getBalance()) != 0) {
                    throw new IllegalArgumentException("Balance does not match transaction history for " + a.getAccountNumber());
                }
            }
            return replay;
        } catch (RuntimeException e) {
            throw new PersistenceException("Inconsistent bank snapshot: " + e.getMessage() + ". Original data preserved.", e);
        }
    }

    private void replayEntry(Map<String, Account> replay, Transaction t, boolean incoming) {
        Account account = replay.get(t.accountNumber());
        if (account == null) { throw new IllegalArgumentException("Transaction references unknown account"); }
        Account changed = incoming ? account.deposit(t.amount()) : account.withdraw(t.amount());
        replay.put(t.accountNumber(), changed.withTransaction(t));
    }

    private void ensureOpen() {
        if (closed) { throw new PersistenceException("Bank store is closed."); }
    }

    @Override public void close() {
        closed = true;
        try {
            if (lock != null && lock.isValid()) { lock.release(); }
        } catch (IOException e) { throw new PersistenceException("Cannot release data lock.", e); }
        finally {
            try { if (lockChannel != null) { lockChannel.close(); } }
            catch (IOException e) { throw new PersistenceException("Cannot close data lock.", e); }
        }
    }
}

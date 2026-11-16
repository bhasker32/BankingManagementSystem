package com.banking;

import com.banking.exception.PersistenceException;
import com.banking.model.*;
import com.banking.persistence.BankStore;
import com.banking.service.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {
    @TempDir Path root;

    @Test void missingDirectoryAndFilesAreCreatedAndEmptyFilesLoad() {
        Path data = root.resolve("nested/data");
        try (BankService bank = new BankService(data, AccountServiceTest.failWarning())) {
            assertTrue(bank.accounts().listAccounts().isEmpty());
            assertTrue(Files.isRegularFile(data.resolve("accounts.txt")));
            assertTrue(Files.isRegularFile(data.resolve("transactions.txt")));
        }
        try (BankService bank = new BankService(data, AccountServiceTest.failWarning())) { assertTrue(bank.accounts().listAccounts().isEmpty()); }
    }

    @Test void bothAccountKindsCustomerDataHistoryAndIdsSurviveReload() {
        try (BankService bank = new BankService(root, AccountServiceTest.failWarning())) {
            create(bank.accounts(), AccountType.SAVINGS);
            create(bank.accounts(), AccountType.CURRENT);
            bank.transactions().deposit("ACC100001", new BigDecimal("100.12"));
            bank.transactions().transfer("ACC100001", "ACC100002", new BigDecimal("25.01"));
        }
        try (BankService bank = new BankService(root, AccountServiceTest.failWarning())) {
            Account a = bank.accounts().findAccount("ACC100001");
            Account b = bank.accounts().findAccount("ACC100002");
            assertInstanceOf(SavingsAccount.class, a);
            assertInstanceOf(CurrentAccount.class, b);
            assertEquals("Alice", a.getCustomer().getName());
            assertEquals("a@example.com", a.getCustomer().getEmail());
            assertEquals(new BigDecimal("75.11"), a.getBalance());
            assertEquals(new BigDecimal("25.01"), b.getBalance());
            assertEquals(2, a.getTransactionHistory().size());
            assertEquals(1, b.getTransactionHistory().size());
            assertEquals("TXN100003", bank.transactions().deposit("ACC100002", BigDecimal.ONE).transactionId());
            assertEquals("ACC100003", create(bank.accounts(), AccountType.SAVINGS).getAccountNumber());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"accounts.txt", "transactions.txt"})
    void malformedRecordsAreReportedWithoutOverwritingOriginalFiles(String filename) throws IOException {
        Path file = root.resolve(filename);
        String bad = "not|a|valid|record\n";
        Files.writeString(file, bad);
        PersistenceException exception = assertThrows(PersistenceException.class, () -> new BankService(root, AccountServiceTest.failWarning()));
        assertTrue(exception.getMessage().contains("line 1"));
        assertEquals(bad, Files.readString(file));
        // Startup failure must release its lock.
        Files.writeString(file, "");
        try (BankService bank = new BankService(root, AccountServiceTest.failWarning())) { assertTrue(bank.accounts().listAccounts().isEmpty()); }
    }

    @Test void duplicateAccountsAndDuplicateCustomersAreRejected() throws IOException {
        String first = "ACC100001|CUST100001|Alice|9876543210|a@example.com|SAVINGS|0.00\n";
        Path file = root.resolve("accounts.txt");
        Files.writeString(file, first + first);
        assertThrows(PersistenceException.class, () -> new BankService(root, AccountServiceTest.failWarning()));
        Files.writeString(file, first + first.replace("ACC100001", "ACC100002"));
        assertThrows(PersistenceException.class, () -> new BankService(root, AccountServiceTest.failWarning()));
    }

    @Test void validLookingButInconsistentBalancesAreRejected() throws IOException {
        try (BankService bank = new BankService(root, AccountServiceTest.failWarning())) { create(bank.accounts(), AccountType.SAVINGS); }
        Path accounts = root.resolve("accounts.txt");
        String edited = Files.readString(accounts).replace("|0.00", "|20.00");
        Files.writeString(accounts, edited);
        assertThrows(PersistenceException.class, () -> new BankService(root, AccountServiceTest.failWarning()));
        assertEquals(edited, Files.readString(accounts));
    }

    @Test void missingOrCorruptTransferPairCannotLoadAsHalfATransfer() throws IOException {
        seedTransfer();
        Path ledger = root.resolve("transactions.txt");
        List<String> lines = Files.readAllLines(ledger);
        Files.write(ledger, lines.subList(0, lines.size() - 1));
        assertThrows(PersistenceException.class, () -> new BankService(root, AccountServiceTest.failWarning()));
        List<String> corrupted = new ArrayList<>(lines);
        corrupted.set(2, corrupted.get(2).replace("|20.00|", "|19.00|"));
        Files.write(ledger, corrupted);
        assertThrows(PersistenceException.class, () -> new BankService(root, AccountServiceTest.failWarning()));
    }

    @Test void duplicateTransactionOperationIdsAreRejected() throws IOException {
        try (BankService bank = new BankService(root, AccountServiceTest.failWarning())) {
            create(bank.accounts(), AccountType.SAVINGS);
            bank.transactions().deposit("ACC100001", BigDecimal.ONE);
            bank.transactions().deposit("ACC100001", BigDecimal.ONE);
        }
        Path ledger = root.resolve("transactions.txt");
        Files.writeString(ledger, Files.readString(ledger).replace("TXN100002", "TXN100001"));
        assertThrows(PersistenceException.class, () -> new BankService(root, AccountServiceTest.failWarning()));
    }

    @ParameterizedTest @ValueSource(strings = {"accounts.txt", "transactions.txt"})
    void losingOneNonemptySnapshotIsDetectedInsteadOfInventingABalance(String filename) throws IOException {
        seedTransfer();
        Files.delete(root.resolve(filename));
        assertThrows(PersistenceException.class, () -> new BankService(root, AccountServiceTest.failWarning()));
    }

    @ParameterizedTest @ValueSource(strings = {"unknown-account", "failed-entry", "wrong-balance"})
    void ledgerSemanticCorruptionIsDetected(String kind) throws IOException {
        seedTransfer();
        Path ledger = root.resolve("transactions.txt");
        String original = Files.readString(ledger);
        String corrupt = switch (kind) {
            case "unknown-account" -> original.replace("ACC100002", "ACC999999");
            case "failed-entry" -> original.replace("SUCCESS", "FAILED");
            default -> original.replace("Cash deposit|50.00", "Cash deposit|49.00");
        };
        Files.writeString(ledger, corrupt);
        assertThrows(PersistenceException.class, () -> new BankService(root, AccountServiceTest.failWarning()));
        assertEquals(corrupt, Files.readString(ledger));
    }

    @Test void secondApplicationCannotOpenTheSameData() {
        try (BankService first = new BankService(root, AccountServiceTest.failWarning())) {
            assertThrows(PersistenceException.class, () -> new BankService(root, AccountServiceTest.failWarning()));
            assertTrue(first.accounts().listAccounts().isEmpty());
        }
    }

    @Test void preCommitWriteFailureLeavesBothBalancesFilesHistoryAndIdsUnchanged() throws IOException {
        try (FailingStore store = new FailingStore(root)) {
            AccountService accounts = new AccountService(store);
            TransactionService transactions = new TransactionService(store);
            create(accounts, AccountType.SAVINGS);
            create(accounts, AccountType.CURRENT);
            transactions.deposit("ACC100001", new BigDecimal("50"));
            String oldAccounts = Files.readString(root.resolve("accounts.txt"));
            String oldTransactions = Files.readString(root.resolve("transactions.txt"));
            store.failJournal = true;
            assertThrows(PersistenceException.class, () -> transactions.transfer("ACC100001", "ACC100002", new BigDecimal("20")));
            assertEquals(new BigDecimal("50.00"), accounts.findAccount("ACC100001").getBalance());
            assertEquals(new BigDecimal("0.00"), accounts.findAccount("ACC100002").getBalance());
            assertEquals(1, transactions.history("ACC100001").size());
            assertTrue(transactions.history("ACC100002").isEmpty());
            assertEquals(oldAccounts, Files.readString(root.resolve("accounts.txt")));
            assertEquals(oldTransactions, Files.readString(root.resolve("transactions.txt")));
            store.failJournal = false;
            assertEquals("TXN100002", transactions.transfer("ACC100001", "ACC100002", new BigDecimal("20")).transactionId());
        }
    }

    @Test void accountCreationWriteFailureDoesNotConsumeAnAccountId() {
        try (FailingStore store = new FailingStore(root)) {
            AccountService accounts = new AccountService(store);
            store.failJournal = true;
            assertThrows(PersistenceException.class, () -> create(accounts, AccountType.SAVINGS));
            assertTrue(accounts.listAccounts().isEmpty());
            store.failJournal = false;
            assertEquals("ACC100001", create(accounts, AccountType.SAVINGS).getAccountNumber());
        }
    }

    @Test void postCommitFailureIsDurableAndRecoversBothFilesAfterRestart() throws IOException {
        try (FailingStore store = new FailingStore(root)) {
            AccountService accounts = new AccountService(store);
            TransactionService transactions = new TransactionService(store);
            create(accounts, AccountType.SAVINGS);
            create(accounts, AccountType.CURRENT);
            transactions.deposit("ACC100001", new BigDecimal("50"));
            store.failTransactions = true; // accounts.txt is replaced, then transactions.txt fails.
            assertEquals("TXN100002", transactions.transfer("ACC100001", "ACC100002", new BigDecimal("20")).transactionId());
            assertEquals(new BigDecimal("30.00"), accounts.findAccount("ACC100001").getBalance());
            assertEquals(new BigDecimal("20.00"), accounts.findAccount("ACC100002").getBalance());
            assertTrue(Files.exists(root.resolve("pending.commit")));
            assertEquals(1, store.warnings.size());
            // A new operation cannot overwrite a journal whose recovery is still failing.
            assertThrows(PersistenceException.class, () -> transactions.deposit("ACC100001", BigDecimal.ONE));
        }
        try (BankService bank = new BankService(root, AccountServiceTest.failWarning())) {
            assertEquals(new BigDecimal("30.00"), bank.accounts().findAccount("ACC100001").getBalance());
            assertEquals(new BigDecimal("20.00"), bank.accounts().findAccount("ACC100002").getBalance());
            assertEquals(2, bank.transactions().history("ACC100001").size());
            assertEquals(1, bank.transactions().history("ACC100002").size());
            assertFalse(Files.exists(root.resolve("pending.commit")));
            assertEquals("TXN100003", bank.transactions().deposit("ACC100001", BigDecimal.ONE).transactionId());
        }
    }

    @Test void malformedJournalIsPreservedAndStartupFailsClearly() throws IOException {
        Path journal = root.resolve("pending.commit");
        Files.writeString(journal, "incomplete");
        assertThrows(PersistenceException.class, () -> new BankService(root, AccountServiceTest.failWarning()));
        assertEquals("incomplete", Files.readString(journal));
    }

    private void seedTransfer() {
        try (BankService bank = new BankService(root, AccountServiceTest.failWarning())) {
            create(bank.accounts(), AccountType.SAVINGS);
            create(bank.accounts(), AccountType.CURRENT);
            bank.transactions().deposit("ACC100001", new BigDecimal("50"));
            bank.transactions().transfer("ACC100001", "ACC100002", new BigDecimal("20"));
        }
    }

    private static Account create(AccountService accounts, AccountType type) { return accounts.createAccount("Alice", "9876543210", "a@example.com", type); }

    private static final class FailingStore extends BankStore {
        boolean failJournal;
        boolean failTransactions;
        final List<String> warnings;
        FailingStore(Path path) { this(path, new ArrayList<>()); }
        FailingStore(Path path, List<String> warnings) { super(path, warnings::add); this.warnings = warnings; }
        @Override protected void writeAtomically(Path destination, List<String> lines) throws IOException {
            if ((failJournal && destination.getFileName().toString().equals("pending.commit"))
                    || (failTransactions && destination.getFileName().toString().equals("transactions.txt"))) {
                throw new IOException("Injected disk failure");
            }
            super.writeAtomically(destination, lines);
        }
    }
}

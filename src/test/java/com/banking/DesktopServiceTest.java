package com.banking;

import com.banking.exception.*;
import com.banking.model.*;
import com.banking.service.*;
import java.math.BigDecimal;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DesktopServiceTest {
    @TempDir Path data;
    private void accounts(BankService bank) {
        bank.accounts().createAccount("Alice", "9876543210", "alice@example.com", AccountType.SAVINGS);
        bank.accounts().createAccount("Bob", "9876543211", "bob@example.com", AccountType.CURRENT);
        bank.transactions().deposit("ACC100001", new BigDecimal("10000"));
        bank.transactions().deposit("ACC100002", new BigDecimal("5000"));
    }
    @Test void previewsDoNotCommitOrConsumeIdsAndExecuteRevalidates() {
        try (BankService bank = new BankService(data, AccountServiceTest.failWarning())) {
            accounts(bank);
            var p = bank.transactions().preview(TransactionType.TRANSFER, "ACC100001", "ACC100002", new BigDecimal("3000"));
            assertEquals(new BigDecimal("7000.00"), p.resultingBalance());
            assertEquals(new BigDecimal("8000.00"), p.destinationBalance());
            assertEquals(new BigDecimal("10000.00"), bank.accounts().findAccount("ACC100001").getBalance());
            assertEquals(2, bank.snapshot().transactionCount());
            assertThrows(ValidationException.class, () -> bank.transactions().preview(TransactionType.TRANSFER, "ACC100001", "ACC100001", BigDecimal.ONE));
            assertThrows(AccountNotFoundException.class, () -> bank.transactions().preview(TransactionType.DEPOSIT, "ACC999999", null, BigDecimal.ONE));
            assertThrows(InvalidAmountException.class, () -> bank.transactions().preview(TransactionType.DEPOSIT, "ACC100001", null, BigDecimal.ZERO));
            assertEquals("TXN100003", bank.transactions().withdraw("ACC100001", new BigDecimal("9000")).transactionId());
            assertThrows(InsufficientBalanceException.class, () -> bank.transactions().transfer("ACC100001", "ACC100002", new BigDecimal("3000")));
            assertEquals(new BigDecimal("5000.00"), bank.accounts().findAccount("ACC100002").getBalance());
        }
    }
    @Test void mandatoryDesktopScenarioWithNoteAndAggregatesSurvivesRestart() {
        try (BankService bank = new BankService(data, AccountServiceTest.failWarning())) {
            accounts(bank);
            bank.transactions().deposit("ACC100001", new BigDecimal("2000"));
            bank.transactions().withdraw("ACC100001", new BigDecimal("2000"));
            bank.transactions().transfer("ACC100001", "ACC100002", new BigDecimal("3000"), "Monthly allocation");
            var snapshot = bank.refresh();
            assertEquals(1, snapshot.savingsAccounts()); assertEquals(1, snapshot.currentAccounts());
            assertEquals(new BigDecimal("15000.00"), snapshot.totalBalance()); assertEquals(5, snapshot.transactionCount());
            assertEquals(6, snapshot.transactions().size());
            assertTrue(snapshot.transactions().get(0).description().contains("Monthly allocation"));
        }
        try (BankService bank = new BankService(data, AccountServiceTest.failWarning())) {
            assertEquals(new BigDecimal("7000.00"), bank.accounts().findAccount("ACC100001").getBalance());
            assertEquals(new BigDecimal("8000.00"), bank.accounts().findAccount("ACC100002").getBalance());
            assertEquals(4, bank.transactions().history("ACC100001").size()); assertEquals(2, bank.transactions().history("ACC100002").size());
            assertThrows(InsufficientBalanceException.class, () -> bank.transactions().transfer("ACC100001", "ACC100002", new BigDecimal("20000")));
            assertEquals(new BigDecimal("7000.00"), bank.accounts().findAccount("ACC100001").getBalance());
            assertEquals(new BigDecimal("8000.00"), bank.accounts().findAccount("ACC100002").getBalance());
            assertEquals("TXN100006", bank.transactions().deposit("ACC100001", BigDecimal.ONE).transactionId());
        }
    }
    @Test void malformedRefreshPreservesSnapshotAndBlocksWritesUntilRepaired() throws Exception {
        try (BankService bank = new BankService(data, AccountServiceTest.failWarning())) {
            accounts(bank); Path file = data.resolve("transactions.txt"); String saved = Files.readString(file);
            Files.writeString(file, "broken record");
            assertThrows(PersistenceException.class, bank::refresh);
            assertEquals(new BigDecimal("15000.00"), bank.snapshot().totalBalance());
            assertThrows(PersistenceException.class, () -> bank.transactions().deposit("ACC100001", BigDecimal.ONE));
            assertEquals("broken record", Files.readString(file));
            Files.writeString(file, saved); bank.refresh();
            assertEquals("TXN100003", bank.transactions().deposit("ACC100001", BigDecimal.ONE).transactionId());
        }
    }
    @Test void transferDescriptionValidationDoesNotCreateTransactions() {
        try (BankService bank = new BankService(data, AccountServiceTest.failWarning())) {
            accounts(bank);
            assertThrows(ValidationException.class, () -> bank.transactions().transfer("ACC100001", "ACC100002", BigDecimal.ONE, "bad|note"));
            assertThrows(ValidationException.class, () -> bank.transactions().transfer("ACC100001", "ACC100002", BigDecimal.ONE, "x".repeat(141)));
            assertEquals(2, bank.snapshot().transactionCount());
            assertEquals(new BigDecimal("10000.00"), bank.accounts().findAccount("ACC100001").getBalance());
        }
    }
}

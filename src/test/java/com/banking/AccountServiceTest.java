package com.banking;

import com.banking.exception.*;
import com.banking.model.*;
import com.banking.service.BankService;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AccountServiceTest {
    @TempDir Path data;

    @Test void createsSavingsAndCurrentWithUniqueSequentialIdsAndCustomerDetails() {
        try (BankService bank = new BankService(data, failWarning())) {
            Account a = bank.accounts().createAccount("Alice", "9876543210", "alice@example.com", AccountType.SAVINGS);
            Account b = bank.accounts().createAccount("Bob", "+919876543211", "bob@example.com", AccountType.CURRENT);
            assertInstanceOf(SavingsAccount.class, a);
            assertInstanceOf(CurrentAccount.class, b);
            assertEquals("ACC100001", a.getAccountNumber());
            assertEquals("ACC100002", b.getAccountNumber());
            assertEquals("CUST100001", a.getCustomer().getCustomerId());
            assertEquals("Alice", a.getCustomer().getName());
            assertEquals("9876543210", a.getCustomer().getPhone());
            assertEquals("alice@example.com", a.getCustomer().getEmail());
            assertEquals(new BigDecimal("0.00"), a.getBalance());
            Account found = bank.accounts().findAccount(a.getAccountNumber());
            assertEquals(a.getAccountNumber(), found.getAccountNumber());
            assertEquals(a.getCustomer().getCustomerId(), found.getCustomer().getCustomerId());
            assertEquals(a.getBalance(), found.getBalance());
            assertEquals(2, bank.accounts().listAccounts().size());
        }
    }

    @Test void unknownAccountThrowsDomainException() {
        try (BankService bank = new BankService(data, failWarning())) {
            assertThrows(AccountNotFoundException.class, () -> bank.accounts().findAccount("ACC999999"));
        }
    }

    @Test void invalidCustomerOrTypeDoesNotCreateAccountOrConsumeId() {
        try (BankService bank = new BankService(data, failWarning())) {
            assertThrows(ValidationException.class, () -> bank.accounts().createAccount(" ", "9876543210", "a@example.com", AccountType.SAVINGS));
            assertThrows(ValidationException.class, () -> bank.accounts().createAccount("Alice", "bad", "a@example.com", AccountType.SAVINGS));
            assertThrows(ValidationException.class, () -> bank.accounts().createAccount("Alice", "9876543210", "bad", AccountType.SAVINGS));
            assertThrows(ValidationException.class, () -> bank.accounts().createAccount("Alice", "9876543210", "a@example.com", null));
            assertTrue(bank.accounts().listAccounts().isEmpty());
            assertEquals("ACC100001", bank.accounts().createAccount("Alice", "9876543210", "a@example.com", AccountType.SAVINGS).getAccountNumber());
        }
    }

    @Test void runtimePolymorphismCalculatesInterestAndEnforcesCurrentLimit() {
        Customer c = new Customer("CUST100001", "Alice", "9876543210", "a@example.com");
        Account savings = new SavingsAccount("ACC100001", c, new BigDecimal("1234.56"));
        Account current = new CurrentAccount("ACC100002", c, new BigDecimal("1234.56"));
        assertEquals(new BigDecimal("49.38"), savings.calculateInterest());
        assertEquals(new BigDecimal("0.00"), current.calculateInterest());
        assertEquals(new BigDecimal("0.00"), ((CurrentAccount) current).getOverdraftLimit());
        assertThrows(InsufficientBalanceException.class, () -> current.withdraw(new BigDecimal("1234.57")));
        assertEquals(new BigDecimal("1234.56"), current.getBalance());
    }

    @Test void returnedCollectionsAndAccountSnapshotsCannotMutateTheBank() {
        try (BankService bank = new BankService(data, failWarning())) {
            Account original = bank.accounts().createAccount("Alice", "9876543210", "a@example.com", AccountType.SAVINGS);
            Account detached = original.deposit(new BigDecimal("100"));
            assertEquals(new BigDecimal("100.00"), detached.getBalance());
            assertEquals(new BigDecimal("0.00"), bank.accounts().findAccount(original.getAccountNumber()).getBalance());
            assertThrows(UnsupportedOperationException.class, () -> bank.accounts().listAccounts().clear());
            assertThrows(UnsupportedOperationException.class, () -> original.getTransactionHistory().clear());
        }
    }

    static java.util.function.Consumer<String> failWarning() { return message -> fail(message); }
}

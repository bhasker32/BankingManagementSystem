package com.banking;

import com.banking.exception.*;
import com.banking.model.*;
import com.banking.service.BankService;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceTest {
    @TempDir Path data;
    BankService bank;
    String a;
    String b;

    @BeforeEach void setup() {
        bank = new BankService(data, AccountServiceTest.failWarning());
        a = bank.accounts().createAccount("Alice", "9876543210", "a@example.com", AccountType.SAVINGS).getAccountNumber();
        b = bank.accounts().createAccount("Bob", "9876543211", "b@example.com", AccountType.CURRENT).getAccountNumber();
    }
    @AfterEach void close() { bank.close(); }

    @Test void depositAndWithdrawalPersistExactBalancesAndHistory() {
        Transaction deposit = bank.transactions().deposit(a, new BigDecimal("123.45"));
        assertEquals("TXN100001", deposit.transactionId());
        assertEquals(TransactionStatus.SUCCESS, deposit.status());
        assertEquals(new BigDecimal("123.45"), balance(a));
        Transaction withdrawal = bank.transactions().withdraw(a, new BigDecimal("23.44"));
        assertEquals("TXN100002", withdrawal.transactionId());
        assertEquals(new BigDecimal("100.01"), balance(a));
        assertEquals(2, bank.transactions().history(a).size());
        assertEquals(balance(a), withdrawal.balanceAfterTransaction());
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"0", "-1", "0.001", "1.230", "1000000000.01"})
    void invalidAmountsNeverChangeBalancesOrCreateHistory(String value) {
        BigDecimal amount = value == null ? null : new BigDecimal(value);
        assertThrows(InvalidAmountException.class, () -> bank.transactions().deposit(a, amount));
        assertThrows(InvalidAmountException.class, () -> bank.transactions().withdraw(a, amount));
        assertThrows(InvalidAmountException.class, () -> bank.transactions().transfer(a, b, amount));
        assertEquals(new BigDecimal("0.00"), balance(a));
        assertEquals(new BigDecimal("0.00"), balance(b));
        assertTrue(bank.transactions().history(a).isEmpty());
        assertTrue(bank.transactions().history(b).isEmpty());
        assertEquals("TXN100001", bank.transactions().deposit(a, BigDecimal.ONE).transactionId());
    }

    @Test void failedWithdrawalPreservesFundsHistoryAndNextId() {
        bank.transactions().deposit(a, new BigDecimal("5000"));
        assertThrows(InsufficientBalanceException.class, () -> bank.transactions().withdraw(a, new BigDecimal("6000")));
        assertEquals(new BigDecimal("5000.00"), balance(a));
        assertEquals(1, bank.transactions().history(a).size());
        assertEquals("TXN100002", bank.transactions().withdraw(a, BigDecimal.ONE).transactionId());
    }

    @Test void successfulTransferHasTwoUnambiguousEntriesSharingOneId() {
        bank.transactions().deposit(a, new BigDecimal("100"));
        Transaction outgoing = bank.transactions().transfer(a, b, new BigDecimal("30.25"));
        Transaction incoming = bank.transactions().history(b).get(0);
        assertEquals(new BigDecimal("69.75"), balance(a));
        assertEquals(new BigDecimal("30.25"), balance(b));
        assertEquals(outgoing.transactionId(), incoming.transactionId());
        assertEquals(a, outgoing.accountNumber());
        assertEquals(b, outgoing.relatedAccountNumber());
        assertEquals(b, incoming.accountNumber());
        assertEquals(a, incoming.relatedAccountNumber());
        assertEquals("Transfer out", outgoing.description());
        assertEquals("Transfer in", incoming.description());
        assertEquals(outgoing.timestamp(), incoming.timestamp());
        assertEquals("TXN100003", bank.transactions().deposit(b, BigDecimal.ONE).transactionId());
    }

    @Test void allFailedTransferPathsLeaveBothBalancesAndHistoriesUnchanged() {
        bank.transactions().deposit(a, new BigDecimal("50"));
        bank.transactions().deposit(b, new BigDecimal("20"));
        assertThrows(AccountNotFoundException.class, () -> bank.transactions().transfer(a, "ACC999999", BigDecimal.ONE));
        assertThrows(AccountNotFoundException.class, () -> bank.transactions().transfer("ACC999999", b, BigDecimal.ONE));
        assertThrows(ValidationException.class, () -> bank.transactions().transfer(a, a, BigDecimal.ONE));
        assertThrows(InsufficientBalanceException.class, () -> bank.transactions().transfer(a, b, new BigDecimal("50.01")));
        assertEquals(new BigDecimal("50.00"), balance(a));
        assertEquals(new BigDecimal("20.00"), balance(b));
        assertEquals(1, bank.transactions().history(a).size());
        assertEquals(1, bank.transactions().history(b).size());
        assertEquals("TXN100003", bank.transactions().transfer(a, b, new BigDecimal("50")).transactionId());
        assertEquals(new BigDecimal("0.00"), balance(a));
    }

    @Test void unknownAccountIsRejectedForDepositWithdrawalAndHistory() {
        assertThrows(AccountNotFoundException.class, () -> bank.transactions().deposit("ACC999999", BigDecimal.ONE));
        assertThrows(AccountNotFoundException.class, () -> bank.transactions().withdraw("ACC999999", BigDecimal.ONE));
        assertThrows(AccountNotFoundException.class, () -> bank.transactions().history("ACC999999"));
    }

    @Test void requiredEndToEndFlowSurvivesRestartWithHistories() {
        bank.transactions().deposit(a, new BigDecimal("10000"));
        bank.transactions().deposit(b, new BigDecimal("5000"));
        bank.transactions().withdraw(a, new BigDecimal("2000"));
        bank.transactions().transfer(a, b, new BigDecimal("3000"));
        assertThrows(InsufficientBalanceException.class, () -> bank.transactions().withdraw(a, new BigDecimal("10000")));
        assertThrows(InsufficientBalanceException.class, () -> bank.transactions().transfer(a, b, new BigDecimal("10000")));
        assertEquals(new BigDecimal("5000.00"), balance(a));
        assertEquals(new BigDecimal("8000.00"), balance(b));
        bank.close();
        bank = new BankService(data, AccountServiceTest.failWarning());
        assertEquals(new BigDecimal("5000.00"), balance(a));
        assertEquals(new BigDecimal("8000.00"), balance(b));
        assertEquals(3, bank.transactions().history(a).size());
        assertEquals(2, bank.transactions().history(b).size());
        assertEquals("TXN100005", bank.transactions().deposit(a, BigDecimal.ONE).transactionId());
    }

    private BigDecimal balance(String number) { return bank.accounts().findAccount(number).getBalance(); }
}

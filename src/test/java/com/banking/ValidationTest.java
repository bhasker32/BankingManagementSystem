package com.banking;

import com.banking.exception.*;
import com.banking.model.*;
import com.banking.util.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {
    @ParameterizedTest @NullSource @ValueSource(strings = {"", " ", "abc", "NaN", "Infinity", "1e3", "1,000", "1.001", "0", "-3", "1000000001"})
    void rejectsInvalidConsoleAmounts(String value) { assertThrows(InvalidAmountException.class, () -> InputValidator.parseAmount(value)); }

    @Test void acceptsAndNormalizesExactMoneyWithoutFloatingPoint() {
        assertEquals(new BigDecimal("0.10"), InputValidator.parseAmount("0.10"));
        assertEquals(new BigDecimal("15.00"), InputValidator.parseAmount(" 15 "));
        assertEquals(new BigDecimal("1000000000.00"), InputValidator.parseAmount("1000000000"));
        assertEquals(new BigDecimal("0.30"), InputValidator.parseAmount("0.10").add(InputValidator.parseAmount("0.20")));
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "A|B", "A\nB", "A\tB"})
    void rejectsEmptyOrUnsafeCustomerNames(String name) {
        assertThrows(ValidationException.class, () -> new Customer("CUST100001", name, "9876543210", "a@example.com"));
    }

    @ParameterizedTest @ValueSource(strings = {"123", "abcdefghij", "123-456-7890", "1234567890123456"})
    void rejectsInvalidPhones(String phone) { assertThrows(ValidationException.class, () -> InputValidator.phone(phone)); }

    @ParameterizedTest @ValueSource(strings = {"plain", "@example.com", "a@", "a@domain", "a b@example.com", "a@example..com"})
    void rejectsInvalidEmails(String email) { assertThrows(ValidationException.class, () -> InputValidator.email(email)); }

    @Test void acceptsInternationalNamesAndValidContactInformation() {
        Customer c = new Customer("CUST100001", " Bhasker Prasad Sah ", "+919876543210", "bhasker@example.com");
        assertEquals("Bhasker Prasad Sah", c.getName());
        assertTrue(c.toString().contains("CUST100001"));
        assertEquals("李明", InputValidator.text("李明", "Name", 100));
    }

    @Test void accountTypeAndIdValidation() {
        assertEquals(AccountType.SAVINGS, InputValidator.accountType(" savings "));
        assertThrows(ValidationException.class, () -> InputValidator.accountType("fixed"));
        assertThrows(ValidationException.class, () -> InputValidator.accountType(null));
        assertThrows(ValidationException.class, () -> InputValidator.identifier("ACC000001", "ACC"));
        assertEquals("ACC100010", new AccountNumberGenerator().next(List.of("ACC100009", "ACC100003")));
        assertEquals("TXN100010", new TransactionIdGenerator().next(List.of("TXN100009", "TXN100009")));
    }

    @Test void balanceAndSequenceLimitsFailSafely() {
        assertThrows(InvalidAmountException.class, () -> InputValidator.balance(new BigDecimal("1000000000000.01")));
        assertThrows(ValidationException.class, () -> new AccountNumberGenerator().next(List.of("ACC999999999999999999")));
        Customer c = new Customer("CUST100001", "Alice", "9876543210", "a@example.com");
        Account maximum = new SavingsAccount("ACC100001", c, InputValidator.MAX_BALANCE);
        assertThrows(InvalidAmountException.class, () -> maximum.deposit(new BigDecimal("0.01")));
        assertEquals(InputValidator.MAX_BALANCE, maximum.getBalance());
    }
}

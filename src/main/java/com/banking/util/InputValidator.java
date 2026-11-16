package com.banking.util;

import com.banking.exception.InvalidAmountException;
import com.banking.exception.ValidationException;
import com.banking.model.AccountType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class InputValidator {
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000.00");
    public static final BigDecimal MAX_BALANCE = new BigDecimal("1000000000000.00");

    private InputValidator() { }

    public static String text(String value, String label, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.chars().anyMatch(c -> c == '|' || Character.isISOControl(c))) {
            throw new ValidationException(label + " is required (max " + maxLength + " characters; no pipes or control characters).");
        }
        return value.strip();
    }

    public static String phone(String value) {
        String result = text(value, "Phone", 16);
        if (!result.matches("\\+?[0-9]{10,15}")) {
            throw new ValidationException("Phone must contain 10 to 15 digits, optionally starting with +.");
        }
        return result;
    }

    public static String email(String value) {
        String result = text(value, "Email", 254);
        if (!result.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+")) {
            throw new ValidationException("Enter a valid email address, such as name@example.com.");
        }
        return result;
    }

    public static AccountType accountType(String value) {
        try { return AccountType.valueOf(text(value, "Account type", 10).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new ValidationException("Account type must be SAVINGS or CURRENT."); }
    }

    public static String identifier(String value, String prefix) {
        if (value == null || !value.matches(prefix + "[1-9][0-9]{5,17}")) {
            throw new ValidationException("Invalid " + prefix + " identifier.");
        }
        return value;
    }

    public static BigDecimal parseAmount(String value) {
        if (value == null || value.length() > 30 || !value.strip().matches("[+-]?[0-9]+(?:\\.[0-9]{1,2})?")) {
            throw new InvalidAmountException("Enter a numeric amount with at most two decimal places (no commas or exponents).");
        }
        return amount(new BigDecimal(value.strip()));
    }

    public static BigDecimal amount(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.compareTo(MAX_AMOUNT) > 0 || value.scale() > 2) {
            throw new InvalidAmountException("Amount must be positive, at most 1,000,000,000.00, with at most two decimal places.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal balance(BigDecimal value) {
        if (value == null || value.scale() > 2 || value.abs().compareTo(MAX_BALANCE) > 0) {
            throw new InvalidAmountException("Balance exceeds the supported range or precision.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
}

package com.banking.model;

import com.banking.exception.ValidationException;
import com.banking.util.InputValidator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/** One account's entry. A transfer has two entries sharing one operation ID. */
public record Transaction(String transactionId, TransactionType transactionType, String accountNumber,
        String relatedAccountNumber, BigDecimal amount, LocalDateTime timestamp,
        TransactionStatus status, String description, BigDecimal balanceAfterTransaction) {
    public Transaction {
        transactionId = InputValidator.identifier(transactionId, "TXN");
        Objects.requireNonNull(transactionType, "Transaction type");
        accountNumber = InputValidator.identifier(accountNumber, "ACC");
        relatedAccountNumber = relatedAccountNumber == null ? "" : relatedAccountNumber;
        if (transactionType == TransactionType.TRANSFER) {
            InputValidator.identifier(relatedAccountNumber, "ACC");
            if (accountNumber.equals(relatedAccountNumber)) { throw new ValidationException("Transfer accounts must differ."); }
            if (!isDirection(description, "Transfer out") && !isDirection(description, "Transfer in")) {
                throw new ValidationException("Transfer direction is required.");
            }
        } else if (!relatedAccountNumber.isEmpty()) {
            throw new ValidationException("Only transfers may reference a related account.");
        }
        amount = InputValidator.amount(amount);
        Objects.requireNonNull(timestamp, "Timestamp");
        Objects.requireNonNull(status, "Status");
        description = InputValidator.text(description, "Description", 160);
        balanceAfterTransaction = InputValidator.balance(balanceAfterTransaction);
    }

    private static boolean isDirection(String description, String direction) {
        return direction.equals(description) || (description != null && description.startsWith(direction + ": ")
                && !description.substring(direction.length() + 2).isBlank());
    }
    public boolean isOutgoingTransfer() { return isDirection(description, "Transfer out"); }
    public boolean isIncomingTransfer() { return isDirection(description, "Transfer in"); }
    public String transferNote() {
        int separator = description.indexOf(": ");
        return separator < 0 ? "" : description.substring(separator + 2);
    }
}

package com.banking.persistence;

import com.banking.exception.PersistenceException;
import com.banking.model.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class TransactionFileHandler {
    public List<String> encode(List<Transaction> transactions) {
        return transactions.stream().map(t -> String.join("|", t.transactionId(), t.transactionType().name(),
                t.accountNumber(), t.relatedAccountNumber(), t.amount().toPlainString(), t.timestamp().toString(),
                t.status().name(), t.description(), t.balanceAfterTransaction().toPlainString())).toList();
    }

    public ArrayList<Transaction> loadTransactions(List<String> lines) {
        ArrayList<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) { continue; }
            try {
                String[] f = line.split("\\|", -1);
                if (f.length != 9) { throw new IllegalArgumentException("Expected 9 fields"); }
                transactions.add(new Transaction(f[0], TransactionType.valueOf(f[1]), f[2], f[3], new BigDecimal(f[4]),
                        LocalDateTime.parse(f[5]), TransactionStatus.valueOf(f[6]), f[7], new BigDecimal(f[8])));
            } catch (RuntimeException e) {
                throw new PersistenceException("Malformed transactions.txt record at line " + (i + 1) + ". Original data preserved.", e);
            }
        }
        return transactions;
    }
}

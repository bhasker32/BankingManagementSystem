package com.banking.persistence;

import com.banking.exception.PersistenceException;
import com.banking.model.*;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;

public final class AccountFileHandler {
    public List<String> encode(Collection<Account> accounts) {
        return accounts.stream().map(a -> {
            Customer c = a.getCustomer();
            return String.join("|", a.getAccountNumber(), c.getCustomerId(), c.getName(), c.getPhone(),
                    c.getEmail(), a.getAccountType().name(), a.getBalance().toPlainString());
        }).toList();
    }

    public LinkedHashMap<String, Account> loadAccounts(List<String> lines) {
        LinkedHashMap<String, Account> accounts = new LinkedHashMap<>();
        HashSet<String> customers = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) { continue; }
            try {
                String[] f = line.split("\\|", -1);
                if (f.length != 7) { throw new IllegalArgumentException("Expected 7 fields"); }
                Customer customer = new Customer(f[1], f[2], f[3], f[4]);
                Account account = switch (AccountType.valueOf(f[5])) {
                    case SAVINGS -> new SavingsAccount(f[0], customer, new BigDecimal(f[6]));
                    case CURRENT -> new CurrentAccount(f[0], customer, new BigDecimal(f[6]));
                };
                if (accounts.putIfAbsent(f[0], account) != null || !customers.add(f[1])) {
                    throw new IllegalArgumentException("Duplicate account or customer ID");
                }
            } catch (RuntimeException e) {
                throw new PersistenceException("Malformed accounts.txt record at line " + (i + 1) + ". Original data preserved.", e);
            }
        }
        return accounts;
    }
}

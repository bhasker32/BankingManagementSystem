package com.banking.util;

import java.util.Collection;

public final class TransactionIdGenerator {
    public String next(Collection<String> existing) { return AccountNumberGenerator.nextId(existing, "TXN"); }
}

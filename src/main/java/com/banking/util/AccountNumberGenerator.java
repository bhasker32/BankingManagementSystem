package com.banking.util;

import java.util.Collection;
import com.banking.exception.ValidationException;

public final class AccountNumberGenerator {
    public String next(Collection<String> existing) { return nextId(existing, "ACC"); }

    static String nextId(Collection<String> existing, String prefix) {
        long maximum = 100000;
        for (String id : existing) {
            InputValidator.identifier(id, prefix);
            maximum = Math.max(maximum, Long.parseLong(id.substring(prefix.length())));
        }
        if (maximum >= 999999999999999999L) { throw new ValidationException("Identifier sequence exhausted."); }
        return prefix + (maximum + 1);
    }
}

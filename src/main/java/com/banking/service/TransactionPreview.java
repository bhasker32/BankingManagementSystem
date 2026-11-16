package com.banking.service;

import java.math.BigDecimal;

/** A non-mutating estimate; execution always repeats all validations. */
public record TransactionPreview(BigDecimal currentBalance, BigDecimal resultingBalance,
        BigDecimal destinationBalance) { }

package com.banking;

import com.banking.service.BankService;
import com.banking.ui.BankingConsole;
import java.io.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BankingConsoleTest {
    @TempDir Path data;

    @Test void invalidMenuTypeAndAmountReturnToMenuAndEofExits() throws IOException {
        String commands = String.join("\n", "xyz", "1", "Alice", "9876543210", "a@example.com", "fixed",
                "1", "Alice", "9876543210", "a@example.com", "SAVINGS", "3", "ACC100001", "abc", "6", "ACC100001", "7", "ACC100001", "8", "2", "ACC100001", "");
        String output = run(commands);
        assertTrue(output.contains("Invalid menu option"));
        assertTrue(output.contains("Account type must be SAVINGS or CURRENT"));
        assertTrue(output.contains("Enter a numeric amount"));
        assertTrue(output.contains("Balance: ₹0.00"));
        assertTrue(output.contains("No transactions yet."));
        assertTrue(output.contains("ACCOUNT DETAILS"));
        assertTrue(output.contains("Goodbye."));
    }

    @Test void exactRequiredFlowWorksThroughConsoleAndRestart() throws IOException {
        String commands = String.join("\n", "1", "Alice", "9876543210", "a@example.com", "SAVINGS",
                "1", "Bob", "9876543211", "b@example.com", "CURRENT",
                "3", "ACC100001", "10000", "3", "ACC100002", "5000",
                "4", "ACC100001", "2000", "5", "ACC100001", "ACC100002", "3000",
                "4", "ACC100001", "10000", "5", "ACC100001", "ACC100002", "10000",
                "6", "ACC100001", "6", "ACC100002", "7", "ACC100001", "7", "ACC100002", "9", "");
        String first = run(commands);
        assertTrue(first.contains("Transfer successful."));
        assertTrue(first.contains("Balance: ₹5,000.00"));
        assertTrue(first.contains("Balance: ₹8,000.00"));
        assertTrue(first.contains("Insufficient balance"));
        assertTrue(first.contains("TXN100004"));
        assertFalse(first.contains("TXN100005"));
        String restarted = run("6\nACC100001\n6\nACC100002\n7\nACC100001\n7\nACC100002\n9\n");
        assertTrue(restarted.contains("Loaded 2 account(s)"));
        assertTrue(restarted.contains("Balance: ₹5,000.00"));
        assertTrue(restarted.contains("Balance: ₹8,000.00"));
        assertTrue(restarted.contains("Transfer out ACC100002"));
        assertTrue(restarted.contains("Transfer in ACC100001"));
    }

    @Test void endOfInputHalfwayThroughCreationDoesNotCreateAnything() throws IOException {
        assertTrue(run("1\nAlice\n").contains("Goodbye."));
        try (BankService bank = new BankService(data, AccountServiceTest.failWarning())) { assertTrue(bank.accounts().listAccounts().isEmpty()); }
    }

    private String run(String commands) throws IOException {
        StringWriter output = new StringWriter();
        try (BankService bank = new BankService(data, AccountServiceTest.failWarning())) {
            new BankingConsole(bank, new StringReader(commands), new PrintWriter(output)).run();
        }
        return output.toString();
    }
}

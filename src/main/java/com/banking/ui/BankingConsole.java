package com.banking.ui;

import com.banking.exception.BankingException;
import com.banking.model.*;
import com.banking.service.BankService;
import com.banking.util.InputValidator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class BankingConsole {
    private static final String LINE = "============================================================";
    private final BankService bank;
    private final BufferedReader input;
    private final PrintWriter output;
    private final NumberFormat money = NumberFormat.getNumberInstance(Locale.US);

    public BankingConsole(BankService bank, Reader input, PrintWriter output) {
        this.bank = bank;
        this.input = new BufferedReader(input);
        this.output = output;
        money.setMinimumFractionDigits(2);
        money.setMaximumFractionDigits(2);
    }

    public void run() throws IOException {
        output.println("Loaded " + bank.accounts().listAccounts().size() + " account(s). Saved histories are available.");
        boolean running = true;
        while (running) {
            output.println("\n" + LINE + "\n          SMART BANKING MANAGEMENT SYSTEM\n" + LINE);
            output.println("1. Create Account\n2. View Account\n3. Deposit Money\n4. Withdraw Money\n5. Transfer Funds\n6. Check Balance\n7. View Transaction History\n8. List All Accounts\n9. Exit");
            try {
                switch (ask("Enter your choice: ")) {
                    case "1" -> create();
                    case "2" -> details(bank.accounts().findAccount(ask("Enter Account Number: ")));
                    case "3" -> change(true);
                    case "4" -> change(false);
                    case "5" -> transfer();
                    case "6" -> {
                        Account a = bank.accounts().findAccount(ask("Enter Account Number: "));
                        output.println("Balance: " + format(a.getBalance()));
                    }
                    case "7" -> history();
                    case "8" -> list();
                    case "9" -> running = false;
                    default -> output.println("Invalid menu option. Enter a number from 1 to 9.");
                }
            } catch (EndOfInput e) { running = false; }
            catch (BankingException e) { output.println("Operation failed.\nReason: " + e.getMessage()); }
            output.flush();
        }
        output.println("Goodbye. All completed operations are saved.");
        output.flush();
    }

    private void create() throws IOException {
        String name = ask("Customer Name: ");
        String phone = ask("Phone: ");
        String email = ask("Email: ");
        AccountType type = InputValidator.accountType(ask("Account Type (SAVINGS/CURRENT): "));
        Account a = bank.accounts().createAccount(name, phone, email, type);
        output.println(LINE + "\nACCOUNT CREATED SUCCESSFULLY\n" + LINE);
        details(a);
    }

    private void details(Account a) {
        Customer c = a.getCustomer();
        output.println("ACCOUNT DETAILS\nAccount Number : " + a.getAccountNumber() + "\nCustomer ID    : " + c.getCustomerId()
                + "\nCustomer Name  : " + c.getName() + "\nPhone          : " + c.getPhone() + "\nEmail          : " + c.getEmail()
                + "\nAccount Type   : " + a.getAccountType() + "\nBalance        : " + format(a.getBalance())
                + "\nAnnual interest estimate: " + format(a.calculateInterest()));
        if (a instanceof CurrentAccount current) { output.println("Overdraft limit: " + format(current.getOverdraftLimit())); }
    }

    private void change(boolean deposit) throws IOException {
        String number = ask("Enter Account Number: ");
        BigDecimal amount = InputValidator.parseAmount(ask("Enter Amount: "));
        Transaction t = deposit ? bank.transactions().deposit(number, amount) : bank.transactions().withdraw(number, amount);
        receipt(t, deposit ? "Deposit successful." : "Withdrawal successful.");
    }

    private void transfer() throws IOException {
        String source = ask("Source Account: ");
        String destination = ask("Destination Account: ");
        BigDecimal amount = InputValidator.parseAmount(ask("Enter Amount: "));
        Transaction t = bank.transactions().transfer(source, destination, amount);
        receipt(t, "Transfer successful.");
        output.println("Destination Balance: " + format(bank.accounts().findAccount(destination).getBalance()));
    }

    private void receipt(Transaction t, String message) {
        output.println(message + "\nTransaction ID : " + t.transactionId() + "\nAmount         : " + format(t.amount())
                + "\nNew Balance    : " + format(t.balanceAfterTransaction()));
    }

    private void history() throws IOException {
        String number = ask("Enter Account Number: ");
        List<Transaction> history = bank.transactions().history(number);
        output.println("TRANSACTION HISTORY\nAccount: " + number);
        output.printf("%-12s %-12s %-18s %-18s %-20s %s%n", "ID", "TYPE", "AMOUNT", "BALANCE", "DATE", "DETAILS");
        for (Transaction t : history) {
            output.printf("%-12s %-12s %-18s %-18s %-20s %s %s%n", t.transactionId(), t.transactionType(),
                    format(t.amount()), format(t.balanceAfterTransaction()), t.timestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    t.description(), t.relatedAccountNumber());
        }
        if (history.isEmpty()) { output.println("No transactions yet."); }
    }

    private void list() {
        output.printf("%-14s %-30s %-12s %s%n", "ACCOUNT NO.", "CUSTOMER NAME", "TYPE", "BALANCE");
        List<Account> accounts = bank.accounts().listAccounts();
        for (Account a : accounts) {
            output.printf("%-14s %-30s %-12s %s%n", a.getAccountNumber(), a.getCustomer().getName(), a.getAccountType(), format(a.getBalance()));
        }
        if (accounts.isEmpty()) { output.println("No accounts yet."); }
    }

    private String format(BigDecimal amount) { return "₹" + money.format(amount); }
    private String ask(String prompt) throws IOException {
        output.print(prompt);
        output.flush();
        String line = input.readLine();
        if (line == null) { throw new EndOfInput(); }
        return line.strip();
    }
    private static final class EndOfInput extends RuntimeException { }
}

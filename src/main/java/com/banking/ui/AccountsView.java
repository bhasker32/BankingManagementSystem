package com.banking.ui;

import com.banking.model.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import javax.swing.*;

final class AccountsView extends JPanel {
    private List<Account> visible = List.of();
    AccountsView(MainWindow window) {
        super(new BorderLayout(0, 20)); setOpaque(false);
        JTextField search = Theme.input("account-search", 22); search.setToolTipText("Search account number, customer name or customer ID");
        JComboBox<String> type = new JComboBox<>(new String[]{"All types", "Savings", "Current"}); type.setName("account-filter");
        JPanel tools = Theme.card(); tools.add(Theme.row(Theme.label("Search", 14, Theme.MUTED), search, type), BorderLayout.CENTER);
        tools.add(Theme.button("+ Create Account", "create-account", true, () -> window.navigate("Create Account", null)), BorderLayout.EAST); add(tools, BorderLayout.NORTH);
        JPanel results = Theme.card(); DataTable table = table("accounts-table", 470); JLabel count = Theme.label("", 13, Theme.MUTED);
        results.add(count, BorderLayout.NORTH); results.add(table);
        results.add(Theme.row(Theme.button("View selected account", "view-account", false, () -> table.selected(i -> window.navigate("Account Details", visible.get(i).getAccountNumber()))),
                Theme.label("Double-click a row or press Enter to open it.", 12, Theme.MUTED)), BorderLayout.SOUTH); add(results);
        Runnable update = () -> {
            String query = search.getText().strip().toLowerCase(Locale.ROOT);
            visible = window.snapshot().accounts().stream().filter(a -> (a.getAccountNumber() + " " + a.getCustomer().getName() + " " + a.getCustomer().getCustomerId()).toLowerCase(Locale.ROOT).contains(query))
                    .filter(a -> type.getSelectedIndex() == 0 || Theme.title(a.getAccountType()).equals(type.getSelectedItem())).toList();
            table.rows(rows(visible), window.snapshot().accounts().isEmpty() ? "No accounts found. Create your first account to get started." : "No matching accounts. Try another search or account type.");
            count.setText(visible.size() + " accounts  ·  Click a column heading to sort");
        };
        Theme.onChange(search, update); type.addActionListener(e -> update.run());
        table.activate(i -> window.navigate("Account Details", visible.get(i).getAccountNumber())); update.run();
    }
    static DataTable table(String name, int height) {
        return new DataTable(name, new String[]{"Account number", "Customer", "Type", "Balance", "Status"},
                new Class<?>[]{String.class, String.class, String.class, BigDecimal.class, String.class}, height);
    }
    static List<Object[]> rows(List<Account> accounts) {
        return accounts.stream().map(a -> new Object[]{a.getAccountNumber(), a.getCustomer().getName(), Theme.title(a.getAccountType()), a.getBalance(), "Active"}).toList();
    }
    static JComponent details(MainWindow window, String number) {
        Account account = window.snapshot().accounts().stream().filter(a -> a.getAccountNumber().equals(number)).findFirst().orElseThrow(() -> new com.banking.exception.AccountNotFoundException(number));
        JPanel page = Theme.column(); JPanel summary = Theme.card(); JPanel body = Theme.column();
        body.add(Theme.label(account.getCustomer().getName(), 24, Theme.TEXT)); body.add(Box.createVerticalStrut(10));
        JTextField id = Theme.input("account-number", 18); id.setText(number); id.setEditable(false);
        body.add(Theme.field("Account number", id, "Select the number to copy it."));
        body.add(Theme.label(Theme.title(account.getAccountType()) + " account  ·  Active", 14, Theme.SUCCESS)); summary.add(body);
        JPanel balance = Theme.column(); balance.add(Theme.label("CURRENT BALANCE", 11, Theme.MUTED)); balance.add(Box.createVerticalStrut(14));
        balance.add(Theme.label(Theme.money(account.getBalance()), 30, Theme.PRIMARY)); balance.add(Box.createVerticalStrut(14));
        balance.add(Theme.label("Annual interest estimate: " + Theme.money(account.calculateInterest()), 13, Theme.MUTED)); summary.add(balance, BorderLayout.EAST); page.add(summary); page.add(Box.createVerticalStrut(20));
        JPanel customer = Theme.card(); customer.add(Theme.label("Customer information", 18, Theme.TEXT), BorderLayout.NORTH);
        JPanel fields = new JPanel(new GridLayout(1, 3, 20, 0)); fields.setOpaque(false);
        fields.add(readOnly("Customer ID", account.getCustomer().getCustomerId())); fields.add(readOnly("Email", account.getCustomer().getEmail())); fields.add(readOnly("Phone", account.getCustomer().getPhone())); customer.add(fields); page.add(customer); page.add(Box.createVerticalStrut(20));
        JPanel actions = Theme.row(); actions.setAlignmentX(LEFT_ALIGNMENT);
        for (String name : new String[]{"Deposit", "Withdraw", "Transfer", "Transactions"}) { actions.add(Theme.button(name.equals("Transactions") ? "View full history" : name, "detail-" + name, name.equals("Deposit"), () -> window.navigate(name, number))); }
        actions.add(Theme.button("Back to accounts", "back-accounts", false, () -> window.navigate("Accounts", null))); page.add(actions); page.add(Box.createVerticalStrut(20));
        JPanel recent = Theme.card(); recent.add(Theme.label("Recent transactions", 18, Theme.TEXT), BorderLayout.NORTH);
        var entries = window.snapshot().transactions().stream().filter(t -> t.accountNumber().equals(number)).limit(5).toList();
        DataTable table = TransactionsView.table("account-history", 270); table.rows(TransactionsView.rows(entries), "No transactions yet for this account.");
        table.activate(i -> TransactionsView.showDetails(window, entries.get(i))); recent.add(table); page.add(recent); return page;
    }
    private static JComponent readOnly(String title, String value) { JTextField field = Theme.input("customer-" + title, 12); field.setText(value); field.setEditable(false); return Theme.field(title, field, ""); }
}

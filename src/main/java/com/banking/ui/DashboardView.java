package com.banking.ui;

import com.banking.model.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.swing.*;

final class DashboardView extends JPanel {
    DashboardView(MainWindow window) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setOpaque(false);
        var data = window.snapshot();
        JPanel stats = new JPanel(new GridLayout(1, 3, 16, 0)); stats.setOpaque(false); stats.setAlignmentX(LEFT_ALIGNMENT);
        stats.setMinimumSize(new Dimension(0, 140));
        stats.add(stat("TOTAL BALANCE", Theme.money(data.totalBalance()), "Across all customer accounts", true));
        JPanel accountsStat = stat("TOTAL ACCOUNTS", String.valueOf(data.accounts().size()), "Customer accounts", false);
        JPanel types = new JPanel(new GridLayout(1, 2, 12, 0)); types.setOpaque(false);
        types.add(Theme.label("Savings  " + data.savingsAccounts(), 13, Theme.MUTED));
        types.add(Theme.label("Current  " + data.currentAccounts(), 13, Theme.MUTED));
        accountsStat.add(types, BorderLayout.SOUTH); stats.add(accountsStat);
        stats.add(stat("TRANSACTIONS", String.valueOf(data.transactionCount()), "Completed banking operations", false));
        add(stats); add(Box.createVerticalStrut(20));
        JPanel actions = Theme.card(); JPanel buttons = new JPanel(new GridLayout(1, 5, 8, 0)); buttons.setOpaque(false);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                int columns = getWidth() < 900 ? 3 : 5;
                GridLayout layout = (GridLayout) buttons.getLayout();
                if (layout.getColumns() != columns) { layout.setRows(0); layout.setColumns(columns); layout.setVgap(8); buttons.revalidate(); revalidate(); }
            }
        });
        actions.setMinimumSize(new Dimension(0, 130));
        String[] names = {"Create Account", "Deposit", "Withdraw", "Transfer", "Transactions"};
        for (String name : names) { buttons.add(Theme.button(name, "quick-" + name, name.equals("Create Account"), () -> window.navigate(name, null))); }
        actions.add(Theme.label("Quick actions", 18, Theme.TEXT), BorderLayout.NORTH); actions.add(buttons); add(actions); add(Box.createVerticalStrut(20));
        JPanel recent = Theme.card(); recent.add(section("Recent activity", Theme.button("View all", "view-transactions", false, () -> window.navigate("Transactions", null))), BorderLayout.NORTH);
        DataTable transactions = new DataTable("recent-transactions", new String[]{"Transaction", "Type", "Account", "Amount", "Date / time", "Status"},
                new Class<?>[]{String.class, String.class, String.class, BigDecimal.class, LocalDateTime.class, String.class}, 275);
        var entries = data.transactions().stream().filter(t -> !t.isIncomingTransfer()).limit(5).toList();
        transactions.rows(entries.stream().map(t -> new Object[]{t.transactionId(), Theme.title(t.transactionType()), t.accountNumber(), t.amount(), t.timestamp(), Theme.title(t.status())}).toList(),
                "No transactions yet. Activity will appear after your first successful operation.");
        transactions.activate(index -> TransactionsView.showDetails(window, entries.get(index))); recent.add(transactions); add(recent); add(Box.createVerticalStrut(20));
        JPanel accounts = Theme.card(); accounts.add(section("Account overview", Theme.button("Manage accounts", "manage-accounts", false, () -> window.navigate("Accounts", null))), BorderLayout.NORTH);
        DataTable table = AccountsView.table("overview-accounts", 190); var overview = data.accounts().stream().limit(4).toList();
        table.rows(AccountsView.rows(overview), "No accounts found. Create your first bank account to get started.");
        table.activate(index -> window.navigate("Account Details", overview.get(index).getAccountNumber())); accounts.add(table); add(accounts);
    }
    private JPanel stat(String label, String value, String hint, boolean accent) {
        JPanel card = Theme.card(); JPanel body = Theme.column();
        body.add(Theme.label(label, 11, Theme.MUTED)); body.add(Box.createVerticalStrut(12));
        JLabel amount = Theme.label(value, 26, accent ? Theme.PRIMARY : Theme.TEXT); amount.setToolTipText(value); amount.setMinimumSize(new Dimension(0, 34));
        body.add(amount); body.add(Box.createVerticalStrut(9)); JTextArea note = Theme.message(hint); note.setFont(note.getFont().deriveFont(12f)); note.setRows(2); body.add(note);
        card.add(body); card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 148)); return card;
    }
    static JPanel section(String title, JComponent action) {
        JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false); p.add(Theme.label(title, 18, Theme.TEXT)); p.add(action, BorderLayout.EAST); return p;
    }
}

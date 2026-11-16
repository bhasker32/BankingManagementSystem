package com.banking.ui;

import com.banking.model.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import javax.swing.*;

final class TransactionsView extends JPanel {
    private List<Transaction> visible = List.of();
    TransactionsView(MainWindow window, String account) {
        super(new BorderLayout(0, 20)); setOpaque(false);
        JTextField search = Theme.input("transaction-search", 20); search.setText(account == null ? "" : account);
        JComboBox<String> filter = new JComboBox<>(new String[]{"All types", "Deposit", "Withdrawal", "Transfer"}); filter.setName("transaction-filter");
        JPanel tools = Theme.card(); tools.add(Theme.row(Theme.label("Search activity", 14, Theme.MUTED), search, filter)); add(tools, BorderLayout.NORTH);
        JPanel results = Theme.card(); JLabel count = Theme.label("", 13, Theme.MUTED); DataTable table = table("transactions-table", 470);
        results.add(count, BorderLayout.NORTH); results.add(table); results.add(Theme.row(Theme.button("View transaction", "view-transaction", false, () -> table.selected(i -> showDetails(window, visible.get(i)))),
                Theme.label("Transfers have an outgoing and an incoming entry.", 12, Theme.MUTED)), BorderLayout.SOUTH); add(results);
        Runnable update = () -> {
            String query = search.getText().strip().toLowerCase(Locale.ROOT);
            visible = window.snapshot().transactions().stream().filter(t -> (t.transactionId() + " " + t.accountNumber() + " " + t.relatedAccountNumber() + " " + t.description()).toLowerCase(Locale.ROOT).contains(query))
                    .filter(t -> filter.getSelectedIndex() == 0 || Theme.title(t.transactionType()).equals(filter.getSelectedItem()))
                    .filter(t -> account == null || !search.getText().equals(account) || t.accountNumber().equals(account)).toList();
            table.rows(rows(visible), "No transactions found. Completed operations appear here; try adjusting your filters."); count.setText(visible.size() + " account entries  ·  Most recent first");
        };
        Theme.onChange(search, update); filter.addActionListener(e -> update.run()); table.activate(i -> showDetails(window, visible.get(i))); update.run();
    }
    static DataTable table(String name, int height) {
        DataTable table = new DataTable(name, new String[]{"Transaction ID", "Type", "Account", "Related account", "Amount", "Date / time", "Status", "Description"},
                new Class<?>[]{String.class, String.class, String.class, String.class, BigDecimal.class, LocalDateTime.class, String.class, String.class}, height);
        table.table().setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {122, 110, 120, 125, 125, 195, 90, 230};
        for (int i = 0; i < widths.length; i++) { table.table().getColumnModel().getColumn(i).setPreferredWidth(widths[i]); }
        return table;
    }
    static List<Object[]> rows(List<Transaction> entries) {
        return entries.stream().map(t -> new Object[]{t.transactionId(), Theme.title(t.transactionType()), t.accountNumber(), t.relatedAccountNumber().isEmpty() ? "—" : t.relatedAccountNumber(), t.amount(), t.timestamp(), Theme.title(t.status()), t.description()}).toList();
    }
    static void showDetails(MainWindow owner, Transaction t) {
        JDialog dialog = new JDialog(owner, "Transaction receipt", true); dialog.setName("receipt"); JPanel content = Theme.card();
        JPanel body = Theme.column(); body.add(Theme.label("Transaction " + Theme.title(t.status()).toLowerCase(), 22, t.status() == TransactionStatus.SUCCESS ? Theme.SUCCESS : Theme.ERROR));
        body.add(Box.createVerticalStrut(18)); JTextArea text = Theme.message("Transaction ID: " + t.transactionId() + "\nType: " + Theme.title(t.transactionType())
                + "\nAccount: " + t.accountNumber() + "\nRelated account: " + (t.relatedAccountNumber().isEmpty() ? "—" : t.relatedAccountNumber())
                + "\n\nAmount: " + Theme.money(t.amount()) + "\nBalance after transaction: " + Theme.money(t.balanceAfterTransaction())
                + "\n\nTimestamp: " + Theme.date(t.timestamp()) + "\nDescription: " + t.description());
        text.setForeground(Theme.TEXT); text.setName("receipt-details"); text.setRows(13); body.add(text); content.add(body);
        content.add(Theme.button("Done", "close-receipt", true, dialog::dispose), BorderLayout.SOUTH); dialog.setContentPane(content);
        dialog.setSize(540, 500); dialog.setLocationRelativeTo(owner); dialog.setVisible(true);
    }
}

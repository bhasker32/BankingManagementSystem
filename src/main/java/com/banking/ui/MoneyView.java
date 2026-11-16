package com.banking.ui;

import com.banking.exception.BankingException;
import com.banking.model.*;
import com.banking.util.InputValidator;
import java.awt.*;
import java.math.BigDecimal;
import javax.swing.*;

/** Shared financial form; preview and execution both delegate to TransactionService. */
final class MoneyView extends JPanel {
    MoneyView(MainWindow window, TransactionType type, String selectedNumber) {
        super(new BorderLayout(24, 0)); setOpaque(false);
        if (window.snapshot().accounts().isEmpty()) {
            JPanel empty = Theme.card(); empty.add(Theme.message("No accounts found. Create an account before making a transaction."));
            empty.add(Theme.button("Create Account", "empty-create", true, () -> window.navigate("Create Account", null)), BorderLayout.SOUTH); add(empty); return;
        }
        JPanel form = Theme.card(); JPanel fields = Theme.column(); fields.add(Theme.label("Transaction details", 20, Theme.TEXT)); fields.add(Box.createVerticalStrut(20));
        JComboBox<Account> source = selector(window, "source-account"), destination = selector(window, "destination-account");
        if (selectedNumber != null) { for (int i = 0; i < source.getItemCount(); i++) { if (source.getItemAt(i).getAccountNumber().equals(selectedNumber)) { source.setSelectedIndex(i); } } }
        if (destination.getItemCount() > 1) { destination.setSelectedIndex(source.getSelectedIndex() == 0 ? 1 : 0); }
        fields.add(Theme.field(type == TransactionType.TRANSFER ? "From account" : "Account", source, ""));
        JLabel balance = Theme.label("", 18, Theme.PRIMARY); balance.setName("current-balance"); fields.add(balance); fields.add(Box.createVerticalStrut(24));
        Runnable updateBalance = () -> { Account a = (Account) source.getSelectedItem(); balance.setText("Available balance  " + Theme.money(a.getAvailableFunds())); };
        source.addActionListener(e -> updateBalance.run()); updateBalance.run();
        if (type == TransactionType.TRANSFER) { fields.add(Theme.field("To account", destination, "Choose a different receiving account.")); }
        JTextField amount = Theme.input("amount", 24); fields.add(Theme.field("Amount (₹)", amount, "Enter a positive amount with up to two decimal places."));
        JTextField note = Theme.input("transfer-note", 24);
        if (type == TransactionType.TRANSFER) { fields.add(Theme.field("Description (optional)", note, "Up to 140 characters. This appears in both account histories.")); }
        JTextArea errors = Theme.message("Your balance preview will appear in the review step."); errors.setRows(3); errors.setName("money-errors"); fields.add(errors);
        Runnable validate = () -> {
            try {
                InputValidator.parseAmount(amount.getText());
                if (type == TransactionType.TRANSFER && source.getSelectedIndex() == destination.getSelectedIndex()) {
                    throw new com.banking.exception.ValidationException("Source and destination accounts must be different.");
                }
                if (!note.getText().isBlank()) { InputValidator.text(note.getText(), "Description", 140); }
                errors.setText("Ready to review. The banking service will validate available funds."); errors.setForeground(Theme.SUCCESS);
            } catch (BankingException e) { errors.setText(e.getMessage()); errors.setForeground(Theme.ERROR); }
        };
        Theme.onChange(amount, validate); Theme.onChange(note, validate); source.addActionListener(e -> validate.run()); destination.addActionListener(e -> validate.run());
        form.add(Theme.formBody(fields), BorderLayout.CENTER);
        form.add(Theme.row(Theme.button("Cancel", "cancel-money", false, () -> window.navigate("Dashboard", null)), Theme.button("Review " + Theme.title(type).toLowerCase(), "review-money", true, () -> {
            try {
                BigDecimal value = InputValidator.parseAmount(amount.getText());
                String from = ((Account) source.getSelectedItem()).getAccountNumber(), to = ((Account) destination.getSelectedItem()).getAccountNumber();
                String description = note.getText(); if (!description.isBlank()) { InputValidator.text(description, "Description", 140); }
                window.task("Checking transaction…", () -> window.bank().transactions().preview(type, from, to, value), preview -> {
                    String summary = "Account: " + from + (type == TransactionType.TRANSFER ? "\nTo account: " + to : "")
                            + "\n\nCurrent balance: " + Theme.money(preview.currentBalance()) + "\nAmount: " + Theme.money(value)
                            + "\nBalance after transaction: " + Theme.money(preview.resultingBalance())
                            + (preview.destinationBalance() == null ? "" : "\nDestination balance: " + Theme.money(preview.destinationBalance()))
                            + (description.isBlank() ? "" : "\nDescription: " + description);
                    confirm(window, "Confirm " + Theme.title(type).toLowerCase(), summary, () -> window.mutate("Saving transaction…", () -> switch (type) {
                        case DEPOSIT -> window.bank().transactions().deposit(from, value);
                        case WITHDRAWAL -> window.bank().transactions().withdraw(from, value);
                        case TRANSFER -> window.bank().transactions().transfer(from, to, value, description);
                    }, transaction -> {
                        window.navigate("Account Details", from); window.success("Transaction completed successfully. " + transaction.transactionId() + " · " + Theme.money(transaction.amount()) + " · " + Theme.date(transaction.timestamp()));
                        TransactionsView.showDetails(window, transaction);
                    }));
                }, error -> { errors.setText(error instanceof BankingException ? error.getMessage() + "\nAvailable: " + Theme.money(((Account) source.getSelectedItem()).getAvailableFunds()) + " · Requested: " + Theme.money(value) : "Unable to review this transaction. Try refreshing your data."); errors.setForeground(Theme.ERROR); window.error(error); });
            } catch (BankingException e) { validate.run(); window.error(e); }
        })), BorderLayout.SOUTH); add(form);
        JPanel guide = Theme.card(); guide.setPreferredSize(new Dimension(265, 100)); guide.add(Theme.message("REVIEW BEFORE YOU CONFIRM\n\n1. Select the correct account.\n\n2. Enter the amount in rupees.\n\n3. Review the new balance and confirm.\n\nA receipt appears after the operation is saved.\n\nWithdrawals and transfers cannot exceed available funds."), BorderLayout.NORTH); add(guide, BorderLayout.EAST); Theme.responsiveGuide(this, guide);
    }
    private JComboBox<Account> selector(MainWindow window, String name) {
        JComboBox<Account> box = new JComboBox<>(window.snapshot().accounts().toArray(Account[]::new)); box.setName(name);
        box.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof Account a) { setText(a.getAccountNumber() + "  ·  " + a.getCustomer().getName()); }
                setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10)); return this;
            }
        }); return box;
    }
    private void confirm(MainWindow owner, String title, String summary, Runnable action) {
        JDialog dialog = new JDialog(owner, title, true); dialog.setName("confirmation");
        JPanel content = Theme.card(); JTextArea details = Theme.message(summary); details.setForeground(Theme.TEXT); details.setRows(11); content.add(details);
        content.add(Theme.row(Theme.button("Cancel", "cancel-confirm", false, dialog::dispose), Theme.button(title, "confirm-money", true, () -> { dialog.dispose(); action.run(); })), BorderLayout.SOUTH);
        dialog.setContentPane(content); dialog.setSize(520, 420); dialog.setLocationRelativeTo(owner); dialog.setVisible(true);
    }
}

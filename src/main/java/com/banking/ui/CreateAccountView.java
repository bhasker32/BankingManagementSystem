package com.banking.ui;

import com.banking.exception.BankingException;
import com.banking.model.AccountType;
import com.banking.util.InputValidator;
import java.awt.*;
import javax.swing.*;

final class CreateAccountView extends JPanel {
    CreateAccountView(MainWindow window) {
        super(new BorderLayout(24, 0)); setOpaque(false);
        JPanel form = Theme.card(); JPanel fields = Theme.column(); fields.add(Theme.label("Customer information", 20, Theme.TEXT)); fields.add(Box.createVerticalStrut(22));
        JTextField name = Theme.input("customer-name", 28), phone = Theme.input("customer-phone", 28), email = Theme.input("customer-email", 28);
        JComboBox<AccountType> type = new JComboBox<>(AccountType.values()); type.setName("customer-type");
        fields.add(Theme.field("Full name", name, "As it should appear on the account.")); fields.add(Theme.field("Phone number", phone, "10–15 digits; international + prefix accepted."));
        fields.add(Theme.field("Email address", email, "Example: name@example.com")); fields.add(Theme.field("Account type", type, "Both account types open with a zero balance."));
        JTextArea errors = Theme.message("Complete the customer details to create an account."); errors.setName("form-errors"); errors.setForeground(Theme.MUTED); errors.setRows(3); fields.add(errors);
        Runnable validate = () -> {
            try { validate(name, phone, email); errors.setText("Ready to create your account."); errors.setForeground(Theme.SUCCESS); }
            catch (BankingException e) { errors.setText(e.getMessage()); errors.setForeground(Theme.ERROR); }
        };
        Theme.onChange(name, validate); Theme.onChange(phone, validate); Theme.onChange(email, validate);
        form.add(Theme.formBody(fields), BorderLayout.CENTER);
        form.add(Theme.row(Theme.button("Cancel", "cancel-create", false, () -> window.navigate("Accounts", null)), Theme.button("Create Account", "submit-create", true, () -> {
            try {
                validate(name, phone, email);
                String n = name.getText(), p = phone.getText(), e = email.getText(); AccountType selected = (AccountType) type.getSelectedItem();
                window.mutate("Creating account…", () -> window.bank().accounts().createAccount(n, p, e, selected), account -> {
                    window.navigate("Account Details", account.getAccountNumber()); window.success("Account created successfully. Account number: " + account.getAccountNumber());
                });
            } catch (BankingException e) { validate.run(); }
        })), BorderLayout.SOUTH); add(form);
        JPanel guidance = Theme.card(); guidance.setPreferredSize(new Dimension(265, 100)); JPanel copy = Theme.column();
        copy.add(Theme.label("A fresh start", 20, Theme.TEXT)); copy.add(Box.createVerticalStrut(18));
        copy.add(Theme.message("An account number is generated automatically once your account is saved.\n\nSavings\nEarn an estimated 4% annual interest. No minimum balance.\n\nCurrent\nManage everyday transactions. No interest; zero overdraft limit.\n\nYou can deposit funds as soon as the account is created.")); guidance.add(copy, BorderLayout.NORTH); add(guidance, BorderLayout.EAST); Theme.responsiveGuide(this, guidance);
    }
    private void validate(JTextField name, JTextField phone, JTextField email) {
        InputValidator.text(name.getText(), "Customer name", 100); InputValidator.phone(phone.getText()); InputValidator.email(email.getText());
    }
}

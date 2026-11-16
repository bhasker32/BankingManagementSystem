package com.banking.model;

import com.banking.util.InputValidator;

public final class Customer {
    private final String customerId;
    private final String name;
    private final String phone;
    private final String email;

    public Customer(String customerId, String name, String phone, String email) {
        this.customerId = InputValidator.identifier(customerId, "CUST");
        this.name = InputValidator.text(name, "Customer name", 100);
        this.phone = InputValidator.phone(phone);
        this.email = InputValidator.email(email);
    }

    public String getCustomerId() { return customerId; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    @Override public String toString() { return name + " (" + customerId + ")"; }
}

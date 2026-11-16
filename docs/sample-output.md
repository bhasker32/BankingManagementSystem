# Sample console session

The excerpts below follow the required scenario in a fresh data directory. Repeated menus and some detail lines are omitted for readability. See [console-transcript.txt](console-transcript.txt) for the complete output captured from the packaged JAR and a second Java process after restart. Timestamps depend on the machine's local clock.

## Main menu

```text
============================================================
          SMART BANKING MANAGEMENT SYSTEM
============================================================
1. Create Account
2. View Account
3. Deposit Money
4. Withdraw Money
5. Transfer Funds
6. Check Balance
7. View Transaction History
8. List All Accounts
9. Exit
Enter your choice:
```

## Scenario 1: Create a savings account

Choose `1`; enter `Bhasker Prasad Sah`, `9876543210`, `bhasker@example.com`, `SAVINGS`.

```text
============================================================
ACCOUNT CREATED SUCCESSFULLY
============================================================
ACCOUNT DETAILS
Account Number : ACC100001
Customer ID    : CUST100001
Customer Name  : Bhasker Prasad Sah
Phone          : 9876543210
Email          : bhasker@example.com
Account Type   : SAVINGS
Balance        : ₹0.00
Annual interest estimate: ₹0.00
```

## Scenario 2: Create a current account

Choose `1`; enter `Ram Sharma`, `9876543211`, `ram@example.com`, `CURRENT`.

```text
ACCOUNT CREATED SUCCESSFULLY
ACCOUNT DETAILS
Account Number : ACC100002
Customer ID    : CUST100002
Customer Name  : Ram Sharma
Phone          : 9876543211
Email          : ram@example.com
Account Type   : CURRENT
Balance        : ₹0.00
Annual interest estimate: ₹0.00
Overdraft limit: ₹0.00
```

## Scenario 3: Deposit into both accounts

Choose `3`, account A, amount `10000`; repeat for B with `5000`.

```text
Deposit successful.
Transaction ID : TXN100001
Amount         : ₹10,000.00
New Balance    : ₹10,000.00

Deposit successful.
Transaction ID : TXN100002
Amount         : ₹5,000.00
New Balance    : ₹5,000.00
```

## Scenario 4: Withdraw from A

Choose `4`, `ACC100001`, `2000`.

```text
Withdrawal successful.
Transaction ID : TXN100003
Amount         : ₹2,000.00
New Balance    : ₹8,000.00
```

## Scenario 5: Transfer from A to B

Choose `5`, source `ACC100001`, destination `ACC100002`, amount `3000`.

```text
Transfer successful.
Transaction ID : TXN100004
Amount         : ₹3,000.00
New Balance    : ₹5,000.00
Destination Balance: ₹8,000.00
```

## Scenario 6: View both balances

Choose `6` once per account.

```text
Enter Account Number: Balance: ₹5,000.00
Enter Account Number: Balance: ₹8,000.00
```

## Scenario 7: View transaction histories

Choose `7` for A and then B. Dates below illustrate the local timestamp format; the complete capture contains actual timestamps and table spacing.

```text
TRANSACTION HISTORY
Account: ACC100001
ID           TYPE         AMOUNT             BALANCE            DATE                 DETAILS
TXN100001    DEPOSIT      ₹10,000.00         ₹10,000.00         2026-11-16 16:39:00  Cash deposit
TXN100003    WITHDRAWAL   ₹2,000.00          ₹8,000.00          2026-11-16 16:39:00  Cash withdrawal
TXN100004    TRANSFER     ₹3,000.00          ₹5,000.00          2026-11-16 16:39:00  Transfer out ACC100002

TRANSACTION HISTORY
Account: ACC100002
TXN100002    DEPOSIT      ₹5,000.00          ₹5,000.00          2026-11-16 16:39:00  Cash deposit
TXN100004    TRANSFER     ₹3,000.00          ₹8,000.00          2026-11-16 16:39:00  Transfer in ACC100001
```

The shared transfer ID identifies one operation. The direction and related account make the two entries unambiguous.

## Scenario 8: Restart the application

Choose `9`, then run the same JAR with the same data directory.

```text
Goodbye. All completed operations are saved.
```

```powershell
java -jar target/smart-banking-management-system-1.0.0.jar --console
```

## Scenario 9: Load persisted data

```text
Loaded 2 account(s). Saved histories are available.
```

Checking A still displays `₹5,000.00`; checking B displays `₹8,000.00`. Both histories contain the same transaction IDs, timestamps and balances. A later deposit receives `TXN100005`; a later new account receives `ACC100003`.

## Scenario 10: Attempt invalid transactions

Attempt a withdrawal of `10000` from A, then a transfer of `10000` from A to B:

```text
Operation failed.
Reason: Insufficient balance: the account's available funds would be exceeded.

Operation failed.
Reason: Insufficient balance: the account's available funds would be exceeded.
```

A remains `₹5,000.00`, B remains `₹8,000.00`, and neither request creates history or consumes a transaction ID. These two failures were also exercised before restart in the captured session.

Additional invalid-input examples covered by console/unit tests:

```text
Enter your choice: xyz
Invalid menu option. Enter a number from 1 to 9.

Enter Amount: abc
Operation failed.
Reason: Enter a numeric amount with at most two decimal places (no commas or exponents).

Source Account: ACC100001
Destination Account: ACC100001
Enter Amount: 1
Operation failed.
Reason: Source and destination must be different accounts.
```

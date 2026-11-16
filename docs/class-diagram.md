# Class diagram

```mermaid
classDiagram
    class Customer {
        -String customerId
        -String name
        -String phone
        -String email
    }
    class Account {
        <<abstract>>
        -String accountNumber
        -Customer customer
        -BigDecimal balance
        -AccountType accountType
        -ArrayList~Transaction~ transactionHistory
        +deposit(BigDecimal) Account
        +withdraw(BigDecimal) Account
        +calculateInterest() BigDecimal
        +getAvailableFunds() BigDecimal
    }
    class SavingsAccount {
        +ANNUAL_INTEREST_RATE BigDecimal
        +calculateInterest() BigDecimal
        +getAvailableFunds() BigDecimal
    }
    class CurrentAccount {
        +OVERDRAFT_LIMIT BigDecimal
        +calculateInterest() BigDecimal
        +getAvailableFunds() BigDecimal
    }
    class Transaction {
        <<record>>
        -String transactionId
        -TransactionType transactionType
        -String accountNumber
        -String relatedAccountNumber
        -BigDecimal amount
        -LocalDateTime timestamp
        -TransactionStatus status
        -String description
        -BigDecimal balanceAfterTransaction
    }
    class BankStore {
        -LinkedHashMap~String,Account~ accounts
        -List~Transaction~ transactions
        +commit(LinkedHashMap, List)
        -recover()
        -validateAndAttach(Map, List)
        +close()
    }
    Customer "1" --> "1" Account : owns
    Account <|-- SavingsAccount
    Account <|-- CurrentAccount
    Account "1" *-- "0..*" Transaction : history
    Account --> AccountType
    Transaction --> TransactionType
    Transaction --> TransactionStatus
    Main --> BankingConsole
    Main --> MainWindow : default desktop launch
    MainWindow --> BankService : background tasks
    MainWindow --> BankSnapshot : cached presentation state
    MainWindow *-- DashboardView
    MainWindow *-- AccountsView : list and details
    MainWindow *-- CreateAccountView
    MainWindow *-- MoneyView : deposit / withdraw / transfer
    MainWindow *-- TransactionsView
    MainWindow --> Theme
    AccountsView --> DataTable
    TransactionsView --> DataTable
    DashboardView --> DataTable
    MoneyView --> TransactionPreview
    BankService --> BankSnapshot : aggregates
    TransactionService --> TransactionPreview : validate without mutation
    Main --> BankService
    BankingConsole --> BankService
    BankService *-- AccountService
    BankService *-- TransactionService
    BankService *-- BankStore : lifecycle
    AccountService --> BankStore : create and lookup
    TransactionService --> BankStore : plan and commit
    BankStore --> AccountFileHandler : account records
    BankStore --> TransactionFileHandler : ledger records
    AccountService --> AccountNumberGenerator
    TransactionService --> TransactionIdGenerator
    AccountService --> InputValidator
    TransactionService --> InputValidator
    BankingException <|-- InvalidAmountException
    BankingException <|-- InsufficientBalanceException
    BankingException <|-- AccountNotFoundException
    BankingException <|-- ValidationException
    BankingException <|-- PersistenceException
```

Each account has one customer record in this exercise. A transfer has two immutable transaction entries, sharing one operation ID and referring to each other through their account numbers. Account operations produce candidate snapshots; only `BankStore` publishes committed state after journal persistence. Persistence flows through the shared store so both handlers participate in the same commit protocol.

The desktop owns a single `BankService`. Views delegate tasks through `MainWindow`; service I/O executes in `SwingWorker`, and presentation updates execute on the event dispatch thread. `MoneyView` obtains a `TransactionPreview`, opens an explicit confirmation, and only then invokes a banking operation. Both interfaces use the same services, model and files.

# Verification and requirement audit

## Desktop enhancement verification

The Swing frontend retains the original backend requirements below. The current full suite has **77 cases: 73 service/console cases and four opt-in desktop workflows**, all verified with `-Dbanking.guiTests=true`. See [frontend architecture, screenshots and the GUI checklist](frontend.md) for the new implementation and the mandatory **7,000 / 8,000** restart scenario. The older **5,000 / 8,000** scenario below remains covered as a regression test. The original console transcript remains valid when launching with `--console`.

The following sections record the original backend verification, before the desktop enhancement.

Verified on 16 November 2026 using Temurin JDK 17.0.20.1, Maven 3.9.9 and JUnit Jupiter 5.11.4 on Windows. The project targets Java 17.

## Build and automated results

Command: `.\mvnw.cmd -B -ntp clean verify`

Result: **BUILD SUCCESS — 69 tests, 0 failures, 0 errors, 0 skipped**. Counts include parameterized test cases, not 69 distinct test methods.

| Test class | Cases | Evidence |
|---|---:|---|
| AccountServiceTest | 5 | Both account types, customer fields, lookup, unique IDs, invalid creation, polymorphic interest, immutable snapshots/collections |
| TransactionServiceTest | 12 | Deposit, withdrawal, transfer, both-sided failure integrity, precision/null/range checks, next IDs, required flow and restart |
| PersistenceTest | 18 | Missing/empty storage, reload, malformed records, duplicate IDs, damaged snapshots, semantic ledger checks, exclusive lock, I/O failure recovery |
| ValidationTest | 31 | Decimal parsing, invalid amounts, names, phones, emails, account type, delimiters, balance limits and sequence exhaustion |
| BankingConsoleTest | 3 | Invalid menu/type/amount, EOF safety, full banking flow and restored display/history |

All test data uses temporary directories. Surefire XML and text reports are under `target/surefire-reports/` after building.

## Packaged application verification

The built executable JAR was launched through three independent `java -jar` processes using one temporary data directory. Production `data/` was not used.

1. Process one created savings A (`ACC100001`) and current B (`ACC100002`), deposited 10,000 and 5,000, withdrew 2,000 from A and transferred 3,000 to B.
2. It attempted the required 10,000 withdrawal and 10,000 transfer from A. Both failed. Displayed and persisted balances remained **A = 5,000.00; B = 8,000.00**. No `TXN100005` appeared.
3. Process two reopened the directory: both balances and all histories were restored. A had three entries and B two; the transfer entries shared `TXN100004`.
4. The actual files contained two account records and five ledger entries covering four operations. No recovery journal remained after normal completion.
5. Process three deposited 1 into A, receiving `TXN100005`, and created another account, receiving `ACC100003`.

The first two process outputs and the inspected persistence files are retained in [console-transcript.txt](console-transcript.txt). The temporary data directory was removed afterward by the temporary-directory context manager.

## Edge-case audit

| Required edge case | Verified by |
|---|---|
| Empty customer name | ValidationTest unsafe-name cases; AccountServiceTest failed creation |
| Invalid phone | ValidationTest phone cases; AccountServiceTest |
| Invalid email | ValidationTest email cases; AccountServiceTest |
| Invalid account type | ValidationTest; BankingConsoleTest menu recovery |
| Invalid menu option | BankingConsoleTest |
| Non-numeric amount | ValidationTest; BankingConsoleTest |
| Zero deposit | TransactionServiceTest invalid amounts |
| Negative deposit | TransactionServiceTest invalid amounts |
| Zero withdrawal | TransactionServiceTest invalid amounts |
| Negative withdrawal | TransactionServiceTest invalid amounts |
| Withdrawal above balance | TransactionServiceTest; packaged JAR |
| Unknown source account | TransactionServiceTest failed transfer paths |
| Unknown destination account | TransactionServiceTest failed transfer paths |
| Same source/destination | TransactionServiceTest failed transfer paths |
| Transfer above balance | TransactionServiceTest; packaged JAR |
| Missing data directory | PersistenceTest nested directory creation |
| Missing data file | PersistenceTest creation and missing nonempty snapshot cases |
| Empty data file | PersistenceTest empty startup/reload |
| Malformed account record | PersistenceTest malformed record case |
| Malformed transaction record | PersistenceTest malformed record case |
| Restart after transactions | TransactionServiceTest, PersistenceTest, BankingConsoleTest, packaged JAR |
| Transaction ID uniqueness | TransactionServiceTest, PersistenceTest duplicate detection and reload |
| Account ID uniqueness | AccountServiceTest, PersistenceTest duplicate detection and reload |

Additional checks cover nulls, excess monetary precision, NaN/Infinity strings, exponent notation, commas, amount limits, balance overflow, delimiter/control-character input, one-sided transfer records, unknown ledger references, incorrect historical balances, failed-status ledger entries, duplicate customer IDs, malformed journals, a second running instance and mid-prompt EOF.

## Persistence failure audit

`PersistenceTest` overrides the protected atomic-write boundary to throw deterministic `IOException`s:

- **Before the journal commit:** account creation/transfer fails; committed balances, history, account IDs, transaction IDs and both text files stay unchanged. Retrying successfully uses the expected next ID.
- **After the journal commit, between snapshot replacements:** the journal already durably contains the successful transfer. Both in-memory balances reflect it, a recovery notice is emitted and the journal is retained. Another operation is rejected while recovery still fails. Reopening with working storage repairs both files, restores linked histories and continues the ID sequence.
- Malformed snapshots and journals fail clearly without silently discarding financial records or overwriting the corrupt content.

These tests simulate ordinary write failures and process interruption, not arbitrary hardware corruption or power-loss durability on every filesystem.

## Final requirement matrix

| Requirement | Implemented | Tested / reviewed |
|---|---|---|
| Unique account number | YES | YES — creation, restart and duplicate-file detection |
| Savings account | YES | YES |
| Current account | YES | YES — zero overdraft policy |
| Customer details | YES | YES |
| Duplicate prevention | YES | YES |
| Deposit | YES | YES |
| Withdrawal | YES | YES |
| Transfer | YES | YES — mirrored entries and both balances |
| Balance display | YES | YES — console and executable JAR |
| Transaction history | YES | YES — console, files and restart |
| Abstraction | YES | YES — abstract Account operations |
| Encapsulation | YES | YES — immutable snapshots and returned collections |
| Inheritance | YES | YES — savings/current subclasses |
| Polymorphism | YES | YES — interest and common operations |
| Collections | YES | YES — lookup, ordered listings and histories |
| Custom exceptions | YES | YES — domain exception assertions |
| File persistence | YES | YES |
| Restart recovery | YES | YES — normal reload and interrupted snapshot repair |
| Unique transaction IDs | YES | YES — one operation ID per successful request |
| Failed transaction safety | YES | YES — validation and persistence errors |
| UML | YES | N/A — reviewed against implemented dependencies |
| README | YES | N/A — setup, rules, storage protocol and limitations reviewed |
| Sample output | YES | N/A — excerpts and actual JAR transcript |
| 10+ tests | YES | YES — 69 cases |

## Code and submission audit

Reviewed all application classes and tests. Financial values use `BigDecimal`, money comparisons use `compareTo`, and input precision is rejected rather than silently rounded. Interest alone uses explicit `HALF_EVEN` rounding. There are no balance setters, UI mutations, mutable static state, fabricated histories, application dependencies, database code or swallowed file-write errors. Shared candidate/commit helpers avoid separate transfer/deposit persistence paths. Removed an unused history reconstruction method during review.

The initial account/transaction files remain empty. Build output, locally downloaded Maven and runtime lock/journal files are ignored by Git. No real customer data or secrets are included. The Maven launcher also corrects the observed stale `JAVA_HOME` by selecting the installed JDK on `PATH`.

Unresolved implementation issues: **none found**. Documented simulation/storage limitations remain intentional.

# Room 4: The God Class

> *"DO NOT SPLIT. THE ORDER OF THE CHECKS MATTERS."*
> The comment at the top of `TransactionManager`, last touched in 2015.

The transaction engine of Rusty Bank is one class. It parses instructions, applies fees
and limits, converts currencies, reverses transactions, writes the audit log and builds the
daily report, and it keeps the entire bank in static fields. Everyone is afraid of it
because nobody remembers why it does what it does.

## The way out

Split it into small, clear pieces **without changing a single thing it does**.

| Tests | Tag | Today | After the escape |
|---|---|---|---|
| `LedgerGoldenMasterTest` (24) | `golden` | green | must stay green |
| `LedgerEscapeTest` (4) | `escape` | red | must turn green |

The golden master is the only complete description of the engine. Its odd corners (which
check runs first, how amounts are rounded, what a reversal forgets to restore) are part
of the behaviour, and they all have to survive.

The escape tests ask for the shape of the code, measured on the text of
`rusty-ledger/src/main/java`:

- two ledgers must be two independent banks;
- no source file longer than 150 lines;
- no method (or other braced block directly inside a class) longer than 30 lines;
- at least 8 source files.

```bash
cd rusty-ledger
mvn test -Dgroups=golden   # behaviour that must survive
mvn test -Dgroups=escape   # the lock on the door
mvn test                   # the whole room
```

## The rules

1. The locked files may not change. They are listed in `LOCKED.sha256` and checked by
   `scripts/verify-locks.sh`.
2. `RustyLedger.openLedger()` must keep its signature. What it returns is up to you.
3. Everything else under `rusty-ledger/src/main` may be rewritten, renamed or deleted.
4. The size limits are there to be met by genuinely splitting the work, not by squeezing
   the same code into fewer lines.

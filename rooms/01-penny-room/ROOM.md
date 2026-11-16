# Room 1: The Penny Room

> *"The books are off by four cents again. Nobody knows why. Nobody has known why since 1999."*

Rusty Bank keeps its money in `double`. Most days that works. Then a customer pays in
0.10 and 0.70 and is told they cannot withdraw 0.80.

## The way out

Make every test in the room pass:

| Tests | Tag | Today | After the escape |
|---|---|---|---|
| `PennyRoomGoldenMasterTest` (13) | `golden` | green | must stay green |
| `PennyRoomEscapeTest` (9) | `escape` | red | must turn green |

```bash
cd rusty-bank
mvn test -Dgroups=golden   # behaviour that must survive
mvn test -Dgroups=escape   # the lock on the door
mvn test                   # the whole room
```

## The rules

1. The locked files may not change. They are listed in `LOCKED.sha256` and checked by
   `scripts/verify-locks.sh`.
2. `RustyBank.openTerminal()` must keep its signature. What it returns is up to you.
3. Everything else under `rusty-bank/src/main` may be rewritten, renamed or deleted.
4. No special-casing test inputs. The fix has to be a real one.

## What the bank is supposed to do

The contract lives in the Javadoc of `TellerTerminal`. In short:

- Balances are exact to the cent, at any size.
- Monthly interest is `balance x rate / 100 / 12`, rounded half-up to the cent.
- A split payment gives every payee the same whole number of cents, and hands the
  leftover cents one each to the payees at the front of the list. No money appears
  or disappears.

## Where the pennies go

Hints, for humans who want to try the room themselves:

- `0.1 + 0.7` is `0.7999999999999999` in binary floating point.
- `0.275 * 100` does not land where `Math.round` needs it to.
- `Math.floor(64.07 * 100)` is `6406`.
- A `double` cannot tell `10000000000000000.00` from `10000000000000000.01`.

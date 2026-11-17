# Room 2: The Time Trap

> *"Settlement dates are wrong again. Only on some servers. Only some days of the year.
> Only sometimes."*

The payments wing of Rusty Bank decides when a payment is processed and when it settles,
how late an overdue payment is, and what "one month later" means. It was written in the
days of `Date`, `Calendar` and `SimpleDateFormat`, and it has been run on a lot of
different machines since.

## The way out

Make every test in the room pass:

| Tests | Tag | Today | After the escape |
|---|---|---|---|
| `TimeTrapGoldenMasterTest` (13) | `golden` | green | must stay green |
| `TimeTrapEscapeTest` (9) | `escape` | red | must turn green |

```bash
cd rusty-payments
mvn test -Dgroups=golden   # behaviour that must survive
mvn test -Dgroups=escape   # the lock on the door
mvn test                   # the whole room
```

## The rules

1. The locked files may not change. They are listed in `LOCKED.sha256` and checked by
   `scripts/verify-locks.sh`.
2. `RustyPayments.openCalendar()` must keep its signature. What it returns is up to you.
3. Everything else under `rusty-payments/src/main` may be rewritten, renamed or deleted.
4. No special-casing test inputs. The fix has to be a real one.

## What the payments calendar is supposed to do

The full contract lives in the Javadoc of `PaymentCalendar`. Read it closely: the
details of the cut-off, the holidays, the date formats and the month-end rule are all
part of it.

The golden tests run on a server that sits in Warsaw, the only place the legacy code is
right. The escape tests run on servers all over the world.

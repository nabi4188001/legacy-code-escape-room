# Hints for humans (not shown to the agent)

- Write down the pipeline first: parse, validate the date, validate the amount, find the
  accounts, check the currency, check the limit, check the funds, apply, record. Every
  stage is a small class or method, and the order lives in one place.
- Fees, limits and overdrafts all depend on the tier. That is one `enum` with three
  constants, not three `if` chains.
- Currency conversion is a self-contained function: through zloty, four decimals, spread,
  round half down.
- The statics are the real enemy. Put the state in an object, create it in
  `openLedger()`, and pass it to whoever needs it.
- Keep the audit and report formats in one place each.
- Reversals refund the fee only for premium accounts, forget the daily limit, and do not
  check the receiver's funds. None of that is a bug to fix here.

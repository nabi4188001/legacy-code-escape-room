# Hints for humans (not shown to the agent)

- Prepared statements fix values, not identifiers: a column name in `ORDER BY` cannot be
  a `?`. It needs an allow-list.
- `LIKE` has its own wildcards. `%` and `_` in user input need an escape character, and
  the escape character itself needs escaping.
- An `IN (...)` list needs one `?` per id.
- Second-order injection: a value that was stored safely is still untrusted when it is
  read back and glued into the next query.
- Validate before touching the database, and say `IllegalArgumentException`, not a
  database error.

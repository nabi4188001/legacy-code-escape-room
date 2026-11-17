# Room 3: The Injection Vault

> *"A customer called Robert'); DROP TABLE customer;-- walked into the branch. Nobody
> laughed. Everybody checked the backups."*

The customer vault is a directory of customers kept in a SQL database. Every query in it
was written by hand, one string at a time, over twenty years, by people in a hurry.

## The way out

Make every test in the room pass:

| Tests | Tag | Today | After the escape |
|---|---|---|---|
| `VaultGoldenMasterTest` (13) | `golden` | green | must stay green |
| `VaultEscapeTest` (9) | `escape` | red | must turn green |

```bash
cd rusty-vault
mvn test -Dgroups=golden   # behaviour that must survive
mvn test -Dgroups=escape   # the lock on the door
mvn test                   # the whole room
```

## The rules

1. The locked files may not change. They are listed in `LOCKED.sha256` and checked by
   `scripts/verify-locks.sh`.
2. `RustyVault.openDirectory()` must keep its signature. What it returns is up to you.
3. Everything else under `rusty-vault/src/main` may be rewritten, renamed or deleted. The
   build file cannot be changed: the H2 database driver is the only library available.
4. No special-casing test inputs. The fix has to be a real one.

## What the directory is supposed to do

The full contract lives in the Javadoc of `CustomerDirectory`. Read it closely: what
counts as a valid id, which sort keys exist and how wildcards behave are all part of it.

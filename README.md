# Legacy Code Escape Room

An AI agent is locked inside **Rusty Bank**, a deliberately awful legacy Java banking
system. Each room is a real legacy problem. The only way out is to refactor the code
until the room's tests pass, without touching the tests and without breaking
anything that worked before.

Modernising old code is one of the biggest jobs AI is being given in banks. This
project turns that job into something you can watch, score and compare across models.

## Rooms

| # | Room | Legacy problem | Status |
|---|---|---|---|
| 1 | [The Penny Room](rooms/01-penny-room/ROOM.md) | Money stored as `double` | Built |
| 2 | The Time Trap | `Date` and `Calendar` around payment cut-offs | Planned |
| 3 | The Injection Vault | SQL built by string concatenation | Planned |
| 4 | The God Class | A 2,000-line `TransactionManager` | Planned |
| 5 | Boss Room | Two transfers racing for one balance | Planned |

## How a room works

- **Golden master tests** record behaviour that works today. They start green and
  must stay green.
- **Escape tests** describe the bug. They start red. The room is escaped when they
  turn green.
- **Locked files** (the tests and the public `TellerTerminal` contract) are
  fingerprinted in each room's `LOCKED.sha256`. Change one and the escape does not count.

## Try it yourself

You need Java 21 and Maven.

```bash
git clone https://github.com/nabi4188001/legacy-code-escape-room.git
cd legacy-code-escape-room

scripts/verify-locks.sh                              # locks intact?
mvn -f rusty-bank/pom.xml test -Dgroups=golden       # 13 green
mvn -f rusty-bank/pom.xml test -Dgroups=escape       # 9 red: you are locked in
```

Now fix `rusty-bank/src/main` until everything is green.

## Roadmap

- [x] Rusty Bank and Room 1 with locked tests
- [ ] The agent loop (read, plan, edit, compile, test, retry) in Java with LangChain4j
- [ ] Rooms 2 to 4, with every agent step logged
- [ ] Replay viewer: watch any escape attempt in the browser
- [ ] Boss room and **RustyBench**: a leaderboard of which models escape which rooms, and at what cost

## A note on the code

Rusty Bank is written from scratch for this project. It contains no code or data
from any real bank. The bugs, sadly, are all real.

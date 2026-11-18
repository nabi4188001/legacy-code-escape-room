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
| 2 | [The Time Trap](rooms/02-time-trap/ROOM.md) | `Date`, `Calendar` and a server clock nobody can trust | Built |
| 3 | [The Injection Vault](rooms/03-injection-vault/ROOM.md) | SQL built by gluing text together | Built |
| 4 | [The God Class](rooms/04-god-class/ROOM.md) | One class that does everything, and a bank kept in static fields | Built |
| 5 | Boss Room | Two transfers racing for one balance | Planned |

## How a room works

- **Golden master tests** record behaviour that works today. They start green and
  must stay green.
- **Escape tests** describe the bug. They start red. The room is escaped when they
  turn green.
- **Locked files** (the tests and the public `TellerTerminal` contract) are
  fingerprinted in each room's `LOCKED.sha256`. Change one and the escape does not count.

## The agent

`escape-agent/` is the prisoner. It is plain Java 21 with no libraries, and it works with
any model provider that speaks the OpenAI chat protocol.

1. **Look.** It copies the room into a sandbox and runs the tests to see what is broken.
2. **Ask.** It shows the model the room brief, the locked files, the current code and
   the failing tests, and asks for whole-file edits.
3. **Apply.** Edits are only accepted inside the room's editable area. An attempt to
   rewrite a test, the contract, the build file or the lock fingerprints is refused
   and reported back to the model.
4. **Judge.** The real Maven build decides: locks intact, golden green, escape green.
5. **Retry.** Failures go back to the model, up to a fixed number of attempts.

Every run is written to `runs/<room>/` as JSON: each plan, each file before and after,
each test result, tokens and timings. That log is what the replay viewer will play back.

## Try it yourself

You need Java 21 and Maven.

```bash
git clone https://github.com/nabi4188001/legacy-code-escape-room.git
cd legacy-code-escape-room

scripts/verify-locks.sh                              # locks intact?
mvn -f rusty-bank/pom.xml test -Dgroups=golden       # 13 green
mvn -f rusty-bank/pom.xml test -Dgroups=escape       # 9 red: you are locked in
```

Room 2 works the same way in `rusty-payments/` (13 green, 9 red).

Fix `rusty-bank/src/main` by hand until everything is green, or send the agent in:

```bash
mvn -q -f escape-agent/pom.xml package -DskipTests
export LLM_API_KEY=your-key
java -jar escape-agent/target/escape-agent.jar --room 01-penny-room
```

| Setting | Default | Meaning |
|---|---|---|
| `LLM_API_KEY` | none | API key of your provider |
| `LLM_BASE_URL` | Gemini's OpenAI-compatible endpoint | Any OpenAI-compatible base URL |
| `LLM_MODEL` | `gemini-3-flash-preview` | Model id at that provider |

`--list-models` prints the model ids your key can use. The agent can also be run from
the repository's Actions tab with the **escape attempt** workflow, once `LLM_API_KEY`
is saved as a repository secret.

## RustyBench

Which models can get out, and what does it cost them? The **bench** workflow sends every model in
`bench/models.json` through every room several times and rebuilds
[`bench/LEADERBOARD.md`](bench/LEADERBOARD.md) from the logs in `runs/`.

- One run is one model in one room with up to five attempts. A room is escaped only when the real
  Maven build says so: locks intact, golden tests green, escape tests green.
- The leaderboard shows escapes per run, attempts needed, tokens and seconds. A run that ends
  because the provider failed (rate limit, outage) is counted as an error and left out of the
  percentages, because it says nothing about the model.
- Everything runs on free tiers: Gemini and Gemma through Google's OpenAI-compatible endpoint.
  Gemma rejects system prompts, so the agent folds them into the first user message
  (`system_as_user` in the catalogue).
- Add a model by adding an entry to `bench/models.json` and a repository secret with its key.

Early findings: the strongest free model escapes all four rooms on the first try, the smaller
Gemma models almost always do too, and Gemini 3.1 Flash-Lite needs about two attempts and never
escaped the God Class. See the leaderboard for current numbers.

## Roadmap

- [x] Rusty Bank and Room 1 with locked tests
- [x] The agent loop: look, ask, apply, judge, retry, with a full run log
- [x] Recorded escapes by real models
- [x] Room 2: the Time Trap
- [x] Rooms 3 and 4: the Injection Vault and the God Class
- [ ] Replay viewer: watch any escape attempt in the browser
- [x] **RustyBench**: a leaderboard of which models escape which rooms, and at what cost
- [ ] Boss room

## A note on the code

Rusty Bank is written from scratch for this project. It contains no code or data
from any real bank. The bugs, sadly, are all real.

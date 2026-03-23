# Multiplayer Trivia Game (Distributed Systems Project)

This project is a **multiplayer trivia game** implemented in Java using **socket-based client/server communication**.

The system is split into **two server applications**:

- **Game Server**: handles users, menus, gameplay, multiplayer rooms, teams, scoring, admin stats.
- **Lookup Server**: a dedicated server that **serves trivia questions** based on criteria (category/difficulty/mixed) and supports **multiple simultaneous requests**.

Clients connect to the **Game Server** using a terminal client (`GameClient`) and play through text commands.

---

## Overall idea

The main idea is to separate responsibilities:

- The **Game Server** focuses on game flow (login, rooms, team formation, timers, scoring).
- The **Lookup Server** focuses on question selection and retrieval (category/difficulty/mixed).

This separation matches distributed-systems requirements: the Game Server becomes a client of the Lookup Server, and the Lookup Server can serve multiple parallel requests.

---

## Project structure (and why it is organized this way)

```
src/
  MainServer.java                 # Game server entry point (main method)

  client/
    GameClient.java               # Terminal client that connects to game server

  data/
    config.txt                    # Ports + lookup server host/port + lookup_only flag
    questions.txt                 # Question bank used by lookup server (and optional fallback)
    users.txt                     # Registered users storage (CSV)
    scores.txt                    # Scores history storage (CSV)

  server/
    handler/
      ClientHandler.java          # Per-client session logic: commands + menus + single/team play

    game/
      GameConfig.java             # Loads config.txt values
      GameRoom.java               # Multiplayer room engine: teams, broadcast, timer, scoring
      Team.java                   # Team model: players, outputs, score
      GameSession.java            # Older/simple session logic (not the main multiplayer engine)

    lookup/
      LookupServerMain.java       # Lookup server entry point (main method)
      LookupRequestHandler.java   # Per-connection request handler (thread-per-client)
      LookupClient.java           # Game server -> lookup server client + status reporting
      LookupProtocol.java         # Text protocol constants (GET_BATCH, OK, NONE, END...)
      QuestionCodec.java          # Encodes/decodes Question to/from a single line

    model/
      User.java                   # User model
      Score.java                  # Score model
      Question.java               # Question model

    repository/
      UserRepository.java         # Repository interfaces (abstraction layer)
      ScoreRepository.java
      QuestionRepository.java

    repositoryImpl/
      FileUserRepository.java     # File-based persistence implementations
      FileScoreRepository.java
      FileQuestionRepository.java

    service/
      AuthService.java            # register/login logic
      ScoreService.java           # score saving + user history
      QuestionService.java        # game-server question access (calls lookup server)
```

### Why this structure?

- **`model/`** keeps simple data classes separate (User/Score/Question).
- **`repository/` + `repositoryImpl/`** separates *what the app needs* (interfaces) from *how data is stored* (file-based implementation). This makes it easy to replace file storage later.
- **`service/`** contains business logic that the handlers/game engine use (auth, scores, question access).
- **`handler/`** contains networking/session logic (how commands are read and interpreted per client).
- **`game/`** contains the multiplayer game engine (rooms, teams, scoring/timers).
- **`lookup/`** is its own mini-module implementing the dedicated lookup server and its protocol.

---

## The “3 programs” you run (2 servers + 1 client)

Even though only **two of them are servers**, you typically run **three programs**:

1. **Lookup Server (server app)**: `server.lookup.LookupServerMain`
2. **Game Server (server app)**: `MainServer`
3. **Client (terminal app)**: `client.GameClient`

---

## Configuration (`src/data/config.txt`)

Example:

```properties
min_room_players=2
max_room_players=4

lookup_host=localhost
lookup_port=6000
lookup_only=true

game_port=5000
```

### Important flag: `lookup_only`

- `lookup_only=true`: the Game Server will **only** use the Lookup Server for questions.
  - If the lookup server is offline, the client will see **LOOKUP SERVER OFFLINE** messages.
- `lookup_only=false`: the Game Server may fallback to local file questions if lookup is unavailable.

---

## How questions are stored (`src/data/questions.txt`)

Each line is:

```
text|category|difficulty|choice1;choice2;choice3;choice4|correctLetter
```

Example:

```
What is 2+2?|Math|easy|3;4;5;6|B
```

Categories used in this project: `Math`, `Science`, `Geography`  
Difficulty: `easy`, `medium`, `hard`  
Mixed: use `*` (or `MIXED`) for category/difficulty.

---

## How to run (Windows / PowerShell)

From the project root:

### 1) Compile

```powershell
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse -Filter *.java src | ForEach-Object FullName)
```

### 2) Start Lookup Server (Terminal 1)

```powershell
java -cp out server.lookup.LookupServerMain
```

### 3) Start Game Server (Terminal 2)

```powershell
java -cp out MainServer
```

### 4) Start one or more clients (Terminal 3, 4, ...)

```powershell
java -cp out client.GameClient
```

Running multiple clients at once is how you test multiplayer/team games and also creates multiple simultaneous calls to the lookup server.

---

## Features

### 1) Authentication (Register/Login)

- Storage: `src/data/users.txt`
- Main logic: `server.service.AuthService`
- Session handling: `server.handler.ClientHandler`

Client commands:

- `REGISTER <name> <username> <password>`
- `LOGIN <username> <password>`

---

### 2) Single Player

Implemented in `ClientHandler`:

- **Custom Trivia**: choose category + difficulty + number of questions.
- **Random Trivia**: mixed questions.

Single-player uses a **15-second timer** per question with countdown messages.

When `lookup_only=true`, the client is explicitly told:

- `QUESTION SOURCE: LOOKUP SERVER`
- If lookup is stopped: `LOOKUP SERVER OFFLINE...`

---

### 3) Multiplayer (Public Rooms)

Public rooms are managed inside `ClientHandler` + `GameRoom`:

- `JOIN_PUBLIC_ROOM` places users into a shared room with auto-balanced teams (`Alpha` / `Beta`).
- `START_PUBLIC_GAME <numQuestions>` starts a mixed-question game (`*` / `*`).

The multiplayer engine:

- broadcasts questions to both teams
- accepts A–D answers within the timer window
- scores teams
- prints a breakdown at the end

---

### 4) Team Mode (Named Teams)

Team mode allows players to form **named teams with unique names**, choose criteria, and play against another named team.

Commands:

- `TEAM_MODE`
- `CREATE_TEAM <teamName> <category> <difficulty> <numQuestions>`
- `JOIN_TEAM <teamName>`
- `START_TEAM_GAME <teamA> <teamB>`

Rules enforced by server:

- Team names are **unique** (`TEAM_NAME_ALREADY_USED`).
- Both teams must exist and contain players.
- The server checks team sizes:
  - If sizes differ, it refuses to start and prints a clear `TEAM_SIZE_ERROR` message.

When a team game starts, all players receive a clean summary:

- category / difficulty / number of questions
- both teams’ rosters (which player is in which team)

---

### 5) Lookup Server (Question Retrieval Server)

Lookup server entry point:

- `server.lookup.LookupServerMain` (default port `6000`)

Protocol (simple text lines):

- Request: `GET_BATCH|<category>|<difficulty>|<count>`
- Response:
  - `OK` then encoded question lines then `END`, OR
  - `NONE` if no matches

Criteria support:

- Category filter (e.g., `Math`)
- Difficulty filter (e.g., `easy`)
- Mixed:
  - Use `*` or `MIXED` to mean “any”

Concurrency:

- `LookupServerMain` creates a **new thread per connection**, which allows **multiple simultaneous calls** from multiple game sessions/clients.

---

### 6) Scores + History

- Storage: `src/data/scores.txt`
- Save: `ScoreService.addScore(...)`
- View history: `ScoreService.getUserScores(...)`

Client menu option:

- `3` shows your score history.

---

### 7) Admin Panel + Statistics

If the user logs in with username `admin`, the server shows an admin panel with statistics like:

- total connected players
- total questions played
- highest score ever
- wins tracking

Main logic lives in `ClientHandler` (admin branch in the command loop).

---

## Most important classes (high level)

- **`MainServer`**: starts the game server and accepts clients.
- **`ClientHandler`**: the heart of the game server; parses commands, manages menus, launches games, manages teams/rooms.
- **`GameRoom`**: multiplayer engine (broadcast, timers, scoring, per-question evaluation).
- **`QuestionService`**: single API for the game server to retrieve questions. In lookup-only mode it forces all questions to come from lookup server.
- **`LookupServerMain` + `LookupRequestHandler`**: lookup server core and concurrency.
- **`LookupClient`**: game server’s client for lookup server, including status detection (OK/NONE/OFFLINE).

---

## “Explain each function” (how to read the code)

This project is designed so each layer has a clear job:

- If you want to understand **network commands and menus**, start with:
  - `server.handler.ClientHandler.run()`
- If you want to understand **multiplayer gameplay**, focus on:
  - `server.game.GameRoom.startGame(...)`
  - `server.game.GameRoom.submitAnswer(...)`
  - `server.game.GameRoom.evaluate(...)`
- If you want to understand **lookup server behavior**, focus on:
  - `server.lookup.LookupRequestHandler.run()`
  - `server.lookup.LookupClient.getBatchResult(...)`
- If you want to understand **data storage**, check:
  - `server.repositoryImpl.FileUserRepository`
  - `server.repositoryImpl.FileScoreRepository`
  - `server.repositoryImpl.FileQuestionRepository`

---

## Demo checklist (for your report)

- Start Lookup Server + Game Server
- Run 2+ clients
- Show:
  - questions come from lookup server (`QUESTION SOURCE: LOOKUP SERVER`)
  - stop lookup server and show client error (`LOOKUP SERVER OFFLINE`)
  - team creation, joining, and equal-size enforcement (`TEAM_SIZE_ERROR`)
  - team roster broadcast at game start
  - multiplayer scoring + breakdown


# Classic Games

Play classic games for two with a friend on the same Wi-Fi, against each other or, in the cooperative ones,
together. A small **Ktor server** relays moves and checks every rule, and an **Android app** (Jetpack Compose) is
the client. The server can run on a **PC**, or **inside one of the phones**, so you don't even need a computer.

| Game | Notes |
|---|---|
| Tic-Tac-Toe | 3 in a row. |
| Battleship | 10x10, fleet of 5 ships, each player only ever sees their own fleet. |
| Connect Four | 7x6, drop discs into columns. |
| Othello | 8x8, flips lines of discs, automatic passing when you have no move. |
| Checkers | American rules: mandatory captures, multi-jumps, kings, draw after 40 moves each without a capture. |
| Dots and Boxes | 5x5 boxes: draw a line between two dots; closing a box scores it and you draw again. |
| Oware | Abapa rules, 12 pits of 4 seeds: capture pits of 2 or 3, feed an empty opponent, 25 seeds win. |
| Mastermind | A duel: each player hides a code of 4 pegs in 6 colours and cracks the other's, 10 guesses each. |
| Hex | 7x7 rhombus: join your two sides of the board with a chain of stones; the second player may swap. |
| Memory | 6x4 cards, 12 pairs: flip two cards; a pair is yours and you flip again, most pairs wins. |
| Quarto | 4x4, 16 pieces (tall/short, dark/light, square/round, hollow/solid): you choose the piece your opponent places; four in a line sharing a trait wins. |
| Brisca | The Spanish card game for two, 40-card deck: no need to follow suit, trumps win, 61 of the 120 points win; swap the 7 of trumps for the face-up card. |
| Maze Guide | Cooperative: a new 8x8 maze every game. The guide sees all of it and sends hints; the walker only sees the 3x3 cells around them and must reach the exit within 1.5 times the shortest way. |
| Hanabi | Cooperative: build five fireworks from 1 to 5 together. You see your partner's cards but not your own; hint, discard or play, with 8 hint tokens and 3 lives shared, for a score out of 25. |

If a phone loses its connection (Wi-Fi drops, the app is killed, ...), its seat and the game are kept for 90
seconds and the app takes them back by itself. See [Reconnection](#reconnection).

```
ClassicGames/
  core/     Game rules, rooms and the WebSocket protocol (Kotlin/JVM). Shared by the PC server and the app.
  server/   The PC launcher: a tiny main() around core.
  app/      The Android app. It is the client, and it can also run core's server inside the phone.
```

All three are one Gradle build: run Gradle from the repository root.

## Run the server on a PC

Running the packaged server (from a [release](../../releases)) needs **Java 17 or newer**. To run it from the
source, from the repository root:

```powershell
.\gradlew.bat :server:run
```

(Building needs **JDK 25** for Gradle itself, see `gradle/gradle-daemon-jvm.properties`. Android Studio's bundled
JDK works; from a terminal point `JAVA_HOME` at it.)

The server listens on port **8080** on all network interfaces. Quick check: open `http://localhost:8080/` for a
tiny browser chat page, or `http://localhost:8080/rooms/new` to get a fresh room code.

For friends to connect from their phones:

1. Phones and PC must be on the **same Wi-Fi**.
2. Find the PC's address with `ipconfig` (the *IPv4 Address*, for example `192.168.1.20`).
3. Allow Java through the **Windows Firewall** on private networks (Windows may ask the first time the server
   starts; if phones can't connect, check this first).

Run the tests (the rules of every game, plus WebSocket tests that play whole games and drop and restore
connections), or build the zip that goes into a release:

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :server:distZip   # -> server\build\distributions\game-server.zip
```

## Run the app

Open the repository root in **Android Studio** and run the `app` module on an emulator or a phone, or build from a
terminal:

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\ClassicGames-debug.apk
```

Notes:

- The SDK location goes in `local.properties` in the repository root (`sdk.dir=...`). Android Studio creates it; it
  is not committed.
- The app targets Android 8.0+ (minSdk 26) and uses plain `http`/`ws` on the local network.

## Host the server on a phone (no PC needed)

1. In the app, switch on **Host the server on this phone**. Android 13+ asks to allow notifications: allow it,
   because the server shows a notification while it runs (with a **Stop** button).
2. The app shows the phone's address, for example `192.168.1.23:8080`. **Copy** puts it on the clipboard.
3. Your friends switch the option *off* (the default), type that address under **Server address**, and press
   **Save**. Phones must be on the same Wi-Fi (or the host phone's own hotspot).
4. Host and join rooms as usual. The host plays through its own server, nothing to type.

The server runs as an Android foreground service, so it keeps running with the screen off or the app in the
background, and it keeps Wi-Fi awake. If the host phone stops the server (or the system kills the app), the rooms
on it are gone, since they only live in memory.

## Play

1. Open the app and enter **your name** and the **server address** (or turn on hosting on this phone), then press
   **Save**. They are remembered.
   - PC server on the Wi-Fi: `<PC address>:8080`, for example `192.168.1.20:8080`.
   - Android emulator reaching a server on your PC: `10.0.2.2:8080` (that is how the emulator reaches the PC).
2. **Host:** tap a game tile (or **Host a room**). The room shows a 4-character code, for example `K7QM`.
3. **Join:** your friend enters the code under **Room code** and taps **Join**.
4. Once both are in, either of you picks a game. You can chat in the room the whole time. When a game ends,
   **Play again** starts the same game with the roles swapped (who goes first, or who guides in Maze Guide,
   alternates).

A room holds two players. **Leave** (or the Back button) frees your seat at once and cancels the game. To play a
different game, leave and host a new room.

## Reconnection

Phones lose connections all the time: Wi-Fi hiccups, a train tunnel, the app being killed in the background. So:

- **The server keeps your seat.** When a connection drops without a goodbye, the player is marked offline and their
  seat, the room and the running game are kept for **90 seconds** (`DEFAULT_GRACE_MILLIS` in `Hub.kt`). The other
  player sees a banner "X lost their connection. Waiting for them to come back...".
- **The app reconnects by itself.** It retries with a short back-off, and immediately when the network comes back
  or the app returns to the foreground. While reconnecting, the board stays on screen and the header says
  "Reconnecting...". It gives up after 3 minutes.
- **It also survives the app being killed.** The app saves its seat (room and a secret token), so when you open it
  again within the grace period it jumps straight back into the room and the game.
- **Nothing is lost.** The server replays the chat history and the current game state on every reconnect, so you
  see what you missed.
- **Only an explicit Leave gives up the seat early.** If you don't come back within 90 seconds the seat is freed
  and the game is cancelled, and the app tells you the room is gone.

## How it works

Everything is server-authoritative: the apps only send *intentions* ("move piece 40 to 33") and draw the state the
server sends back, so neither app can cheat or get out of sync.

- `GET /rooms/new` returns a random unused room code.
- `ws://<server>:8080/ws?name=<you>&room=<code>` joins a room (max 2 players, unique names per room). Adding
  `&token=<seat token>` takes an existing seat back (see Reconnection).
- Client messages are JSON with a `type`: `chat`, `startGame` (a `game`: `tictactoe`, `battleship`, `connectfour`,
  `othello`, `checkers`, `dotsandboxes`, `oware`, `mastermind`, `hex`, `memory`, `quarto`, `brisca`, `maze` or
  `hanabi`), `move` (a `cell`, which for Dots and Boxes is a line number, for Oware a pit number and for Memory a card
  number, `from` and `to` for checkers, a `code` for a Mastermind guess, a `piece` for the Quarto piece you give your
  opponent, a `card` for the Brisca card you play, a `direction`, `up`, `down`, `left` or `right`, for the Maze Guide
  walker's step, or a Hanabi `action`: `hint` with a `color` or a `number`, or `discard` or `play` with the `card` at
  that position in your hand), `place` (Battleship fleet), `secret` (your Mastermind `code`), `swap` (Hex swap rule),
  `swap7` (Brisca: swap the 7 of trumps for the face-up card), `hint` (Maze Guide: the guide sends a `direction`),
  `leave`.
- The server replies with `chat`, `system`, `error` (only to the sender) and `game` messages. Each game has its own
  state object; Battleship, Mastermind, Brisca, Maze Guide and Hanabi send every player a different view so ships,
  codes, hands and the maze stay hidden (in Hanabi you get your partner's cards and only what hints told you about
  your own), and Memory never sends the face of a card that is face down. Every `game` message has a `version` that
  grows with each change: two updates can arrive out of order, so ignore one older than the last you applied. Right
  after you connect it also sends `session` (your seat token), `history` (recent chat) and, if a game is running, its
  current state; `presence` tells you when the other player goes offline or comes back.
- Games can be competitive or cooperative. A competitive game ends with a `winner` or a draw; a cooperative one (Maze
  Guide, Hanabi) has no winner: its `status` is the same for both players (`won` or `lost`, and for Hanabi also
  `finished` with a `score` out of 25), with a `result` sentence. Either way, leaving in the middle cancels the game
  for the other player.

The full message definitions are documented in `core/src/main/kotlin/com/classicgames/core/Protocol.kt`, which the
server and the app both use.

### Where things are

| What | Where (under `core/src/main/kotlin/com/classicgames/core`, `app/src/main/kotlin/com/classicgames/app`) |
|---|---|
| Rooms, seats, grace period, history | `core`: `Hub.kt`, `GameServer.kt` |
| Game rules | `core`: `TicTacToe.kt`, `Battleship.kt`, `ConnectFour.kt`, `Othello.kt`, `Checkers.kt`, `DotsAndBoxes.kt`, `Oware.kt`, `Mastermind.kt`, `Hex.kt`, `Memory.kt`, `Quarto.kt`, `Brisca.kt`, `Maze.kt`, `Hanabi.kt` |
| Shared game interface and protocol | `core`: `Game.kt`, `Protocol.kt` |
| Connection, reconnecting, saved seat | `app`: `data/ChatClient.kt`, `ui/AppViewModel.kt`, `data/SettingsStore.kt` |
| The server inside the phone | `app`: `server/ServerService.kt`, `server/PhoneServer.kt` |
| Screens | `app`: `ui/LobbyScreen.kt`, `ui/ChatScreen.kt`, one `*Panel.kt` per game |
| Lobby game list and icons | `app`: `ui/Games.kt` |

### Adding a game

1. **core:** write a class implementing `Game` (`handle` applies a move, `messageFor` builds each player's view),
   add its state class and a field on `ServerMessage` in `Protocol.kt`, and register it in `Hub.kt`
   (`KNOWN_GAMES` and the `when` in `startGame`). Add tests next to the others in `core/src/test`. A game may be
   cooperative: the Hub only needs `finished`, so give the state a shared `status` (`playing`, `won`, `lost`) and a
   result instead of a winner. A new client message type must also be routed to `Hub.act` in `GameServer.kt`.
2. **app:** keep the new state in `RoomUi` and `applyMessage` in `AppViewModel.kt` (the protocol classes come from
   core, there is nothing to copy), write a `*Panel.kt` composable, show it in `ChatScreen.kt`, and add one line to
   `GAMES` in `ui/Games.kt` (plus an icon) so it appears in the lobby.

## Known limits

- Rooms only live in the server's memory: restarting the server (or the host phone's server) ends all rooms.
- While the app is in the background, Android may suspend its connection; the app reconnects when you come back
  (the host phone's *server* keeps running in the background).
- After a game ends you can only replay the same game; switching games means leaving and hosting a new room.
- No accounts or passwords: anyone on the network who knows a room code can join it. (Taking over a seat needs its
  secret token, so nobody can take your place after a drop.)

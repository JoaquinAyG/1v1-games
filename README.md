# Classic Games

Play 1v1 classic games with a friend on the same Wi-Fi. A small **Ktor server** relays moves and checks every
rule, and an **Android app** (Jetpack Compose) is the client.

| Game | Notes |
|---|---|
| Tic-Tac-Toe | 3 in a row. |
| Battleship | 10x10, fleet of 5 ships, each player only ever sees their own fleet. |
| Connect Four | 7x6, drop discs into columns. |
| Othello | 8x8, flips lines of discs, automatic passing when you have no move. |
| Checkers | American rules: mandatory captures, multi-jumps, kings, draw after 40 moves each without a capture. |

```
ClassicGames/
  server/   Ktor + WebSockets server (Kotlin). One Gradle project.
  app/      Android app (Kotlin, Jetpack Compose). One Gradle project.
```

## Run the server

You need a **JDK 17 or newer** (Android Studio's bundled JDK works). From the `server` folder:

```powershell
.\gradlew.bat run
```

The server listens on port **8080** on all network interfaces. Quick check: open `http://localhost:8080/` for a
tiny browser chat page, or `http://localhost:8080/rooms/new` to get a fresh room code.

For friends to connect from their phones:

1. Phones and PC must be on the **same Wi-Fi**.
2. Find the PC's address with `ipconfig` (the *IPv4 Address*, for example `192.168.1.20`).
3. Allow Java through the **Windows Firewall** on private networks (Windows may ask the first time the server
   starts; if phones can't connect, check this first).

Run the server's tests (rules for every game, plus WebSocket tests that play whole games):

```powershell
.\gradlew.bat test
```

## Run the app

Open the `app` folder in **Android Studio** and run it on an emulator or a phone, or build from a terminal:

```powershell
.\gradlew.bat assembleDebug
adb install -r build\outputs\apk\debug\ClassicGames-debug.apk
```

Notes:

- Gradle itself needs **JDK 25** (see `app/gradle/gradle-daemon-jvm.properties`). Android Studio's bundled JDK is
  fine; from a terminal point `JAVA_HOME` at it.
- The SDK location goes in `app/local.properties` (`sdk.dir=...`). Android Studio creates it; it is not committed.
- The app targets Android 8.0+ (minSdk 26) and uses plain `http`/`ws` on the local network.

## Play

1. Open the app and enter **your name** and the **server address**, then press **Save**. Both are remembered.
   - Real phone: `<PC address>:8080`, for example `192.168.1.20:8080`.
   - Android emulator: `10.0.2.2:8080` (that is how the emulator reaches the PC).
2. **Host:** tap a game tile (or **Host a room**). The room shows a 4-character code, for example `K7QM`.
3. **Join:** your friend enters the code under **Room code** and taps **Join**.
4. Once both are in, either of you picks a game. You can chat in the room the whole time. When a game ends,
   **Play again** starts the same game with the roles swapped (who goes first alternates).

A room holds two players. If someone leaves in the middle of a game, the game is cancelled. To play a different
game, leave and host a new room.

## How it works

Everything is server-authoritative: the apps only send *intentions* ("move piece 40 to 33") and draw the state the
server sends back, so neither app can cheat or get out of sync.

- `GET /rooms/new` returns a random unused room code.
- `ws://<server>:8080/ws?name=<you>&room=<code>` joins a room (max 2 players, unique names per room).
- Client messages are JSON with a `type`: `chat`, `startGame`, `move` (a `cell`, or `from` and `to` for
  checkers), `place` (Battleship fleet).
- The server replies with `chat`, `system`, `error` (only to the sender) and `game` messages. Each game has its own
  state object; Battleship sends every player a different view so ships stay hidden.

The full message definitions are documented in `server/src/main/kotlin/server/Protocol.kt` (mirrored by
`app/src/main/kotlin/com/classicgames/app/data/Protocol.kt`).

### Where things are

| What | Server (`server/src/main/kotlin/server`) | App (`app/src/main/kotlin/com/classicgames/app`) |
|---|---|---|
| Rooms, players, starting games | `Hub.kt`, `Main.kt` | `ui/AppViewModel.kt`, `data/ChatClient.kt` |
| Game rules | `TicTacToe.kt`, `Battleship.kt`, `ConnectFour.kt`, `Othello.kt`, `Checkers.kt` | |
| Shared game interface | `Game.kt` | |
| Screens | | `ui/LobbyScreen.kt`, `ui/ChatScreen.kt`, one `*Panel.kt` per game |
| Lobby game list and icons | | `ui/Games.kt` |

### Adding a game

1. **Server:** write a class implementing `Game` (`handle` applies a move, `messageFor` builds each player's view),
   add its state class and a field on `ServerMessage` in `Protocol.kt`, and register it in `Hub.kt`
   (`KNOWN_GAMES` and the `when` in `startGame`). Add tests next to the others in `server/src/test`.
2. **App:** mirror the state class in `data/Protocol.kt`, keep it in `RoomUi` and `applyEvent` in `AppViewModel.kt`,
   write a `*Panel.kt` composable, show it in `ChatScreen.kt`, and add one line to `GAMES` in `ui/Games.kt`
   (plus an icon) so it appears in the lobby.

## Known limits

- The connection only lives while the app is open (no background service, no reconnect).
- After a game ends you can only replay the same game; switching games means leaving and hosting a new room.
- The server runs on a PC. Running it inside the phone app is not built yet.
- No accounts or passwords: anyone on the network who knows a room code can join it.

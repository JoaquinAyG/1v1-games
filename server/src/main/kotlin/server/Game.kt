package server

/**
 * A game running in a room. Implementations hold the rules and state and know nothing about sockets.
 * The Hub calls [handle] for every game-related message and then sends [messageFor] to each player,
 * so a game can hide information (like Battleship's ship positions) by giving each player a different view.
 */
interface Game {
    /** True once the game has ended; a finished game can be replaced by a new one. */
    val finished: Boolean

    /** Applies [msg] from [player]. Returns an error message to show only to that player, or null if it was accepted. */
    fun handle(player: String, msg: ClientMessage): String?

    /** The "game" message describing the current state from [player]'s point of view. */
    fun messageFor(player: String): ServerMessage
}

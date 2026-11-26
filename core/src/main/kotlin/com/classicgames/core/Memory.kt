package com.classicgames.core

import java.security.SecureRandom

/**
 * Memory (pairs) on a grid of [columns] x [rows] face-down cards, which hold every face exactly twice. Players take
 * turns flipping two cards. Two cards with the same face are a pair: the player keeps it and flips again. Two
 * different faces end the turn; they stay face up so both players can see them, until the next player flips their
 * first card, which turns them face down again. When every pair is taken, whoever has more pairs wins; as many pairs
 * each is a draw. [first] flips first.
 *
 * Cards are numbered row by row from the top-left: card = row * columns + column. Faces are numbered 0 until the
 * number of pairs. The server shuffles the cards and never tells anyone the face of a card that is face down.
 *
 * [deck] (the face of every card, row by row) exists so tests can choose where the pairs are; rooms always shuffle
 * [COLUMNS] x [ROWS] cards with a SecureRandom.
 */
class Memory(
    val first: String,
    val second: String,
    val columns: Int = COLUMNS,
    val rows: Int = ROWS,
    deck: List<Int>? = null,
) : Game {
    enum class Status { PLAYING, WON, DRAW }

    private val pairs = columns * rows / 2

    private val faces: IntArray = (deck ?: (0 until pairs).flatMap { listOf(it, it) }.shuffled(SecureRandom()))
        .toIntArray()

    init {
        require(columns >= 1 && rows >= 1 && columns * rows % 2 == 0) { "the grid needs an even number of cards" }
        require(faces.size == columns * rows && (0 until pairs).all { face -> faces.count { it == face } == 2 }) {
            "the deck must hold every face 0 until $pairs exactly twice"
        }
    }

    /** Who took each card: '.' nobody yet, '1' the first player, '2' the second. */
    private val owners = CharArray(columns * rows) { '.' }

    /** '1' or '2': the player who flips next. */
    var turn = '1'
        private set
    var status = Status.PLAYING
        private set
    var winner: String? = null
        private set

    /** The card flipped first in the current turn, while the second one is still to come. */
    private var flipped: Int? = null

    /** The last two cards turned over, until the next turn's first flip; face up whether they matched or not. */
    private var lastPair: List<Int> = emptyList()
    private var lastMatched = false

    override val finished get() = status != Status.PLAYING

    override fun handle(player: String, msg: ClientMessage): String? =
        if (msg.type == "move") flip(player, msg.cell ?: -1) else "Memory only understands moves"

    /** Turns [card] face up for [player]. Returns an error message, or null if the card was flipped. */
    fun flip(player: String, card: Int): String? {
        if (status != Status.PLAYING) return "The game is over"
        val mark = when (player) {
            first -> '1'
            second -> '2'
            else -> return "You are not in this game"
        }
        if (mark != turn) return "Not your turn"
        if (card !in faces.indices) return "Invalid card"
        if (owners[card] != '.') return "That pair is already taken"
        if (card == flipped) return "That card is already face up"

        val firstCard = flipped
        if (firstCard == null) {
            // A new turn: the pair left showing by the last one goes face down (or just stays taken).
            lastPair = emptyList()
            flipped = card
            return null
        }

        flipped = null
        lastPair = listOf(firstCard, card)
        lastMatched = faces[firstCard] == faces[card]
        if (!lastMatched) {
            turn = if (turn == '1') '2' else '1'
            return null
        }
        owners[firstCard] = mark
        owners[card] = mark
        if (owners.none { it == '.' }) {
            val one = score('1')
            val two = score('2')
            when {
                one > two -> { status = Status.WON; winner = first }
                two > one -> { status = Status.WON; winner = second }
                else -> status = Status.DRAW
            }
        }
        // A pair earns another turn: the turn stays.
        return null
    }

    /** Pairs taken by [mark]. */
    private fun score(mark: Char) = owners.count { it == mark } / 2

    private fun nameOf(mark: Char) = if (mark == '1') first else second

    /** True for the cards whose face may be shown: taken, flipped this turn, or the last pair still face up. */
    private fun faceUp(card: Int) = owners[card] != '.' || card == flipped || card in lastPair

    override fun messageFor(player: String) =
        ServerMessage(type = "game", from = "server", text = describe(), memory = toState())

    fun toState() = MemoryState(
        game = ID,
        columns = columns,
        rows = rows,
        faces = faces.indices.map { if (faceUp(it)) faces[it] else -1 },
        taken = String(owners),
        firstPlayer = first,
        secondPlayer = second,
        turn = turn.toString(),
        status = status.name.lowercase(),
        winner = winner,
        firstScore = score('1'),
        secondScore = score('2'),
        flipped = listOfNotNull(flipped),
        lastPair = lastPair,
        lastMatched = lastMatched,
    )

    /** Short human-readable summary, for clients that only show text. */
    fun describe(): String {
        val score = "${score('1')}-${score('2')}"
        return when (status) {
            Status.WON -> "Memory: $winner won $score"
            Status.DRAW -> "Memory: draw $score"
            Status.PLAYING -> when {
                lastPair.isNotEmpty() && lastMatched -> "Memory: ${nameOf(turn)} found a pair and goes again, $score"
                lastPair.isNotEmpty() -> "Memory: no match, ${nameOf(turn)}'s turn, $score"
                else -> "Memory: ${nameOf(turn)}'s turn, $score"
            }
        }
    }

    companion object {
        const val ID = "memory"

        /** Cards per row in rooms. */
        const val COLUMNS = 6

        /** Rows of cards in rooms. */
        const val ROWS = 4
    }
}

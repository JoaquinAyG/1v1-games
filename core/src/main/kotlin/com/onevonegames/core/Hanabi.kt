package com.onevonegames.core

import java.security.SecureRandom
import java.util.Random

/**
 * Hanabi for two players, a cooperative card game: together the players build five fireworks, one per colour, each from
 * 1 up to 5. Both win or lose together.
 *
 * The deck has 50 cards: in each of the five colours (red, yellow, green, blue, white) three 1s, two 2s, two 3s, two 4s
 * and one 5. Cards are numbered as described on [HanabiState]. Each player holds 5 cards facing away from them: they see
 * their partner's hand but never their own. There are 8 hint tokens and 3 lives, shared. [first] plays first, and then
 * the players take turns. On their turn a player does exactly one of these:
 *  - Hint (costs a token, not allowed with none left): name one colour or one number. Every card of that colour or
 *    number in the partner's hand is pointed out, and the hint must point out at least one. The others learn they are
 *    not that colour or number.
 *  - Discard a card from their hand (gains a token, not allowed with all 8), then draw.
 *  - Play a card from their hand, then draw. If it is the next number of its colour's firework it goes there, and
 *    completing a firework with its 5 gives a token back if there are fewer than 8. Otherwise it goes to the discard
 *    pile and a life is lost.
 *
 * The game ends at once when the last life is lost ("lost") or when all five fireworks reach 5 (25 points, "won").
 * Otherwise, when a player draws the last card of the deck, each player gets exactly one more turn, the partner first,
 * and then the game is "finished" with the sum of the fireworks as the score, out of 25.
 *
 * The server tracks what each player has been told about each card in their hand, and that knowledge stays with the card
 * when the hand shifts after a play or a discard. A drawn card goes to the end of the hand. A player is never sent their
 * own cards, nor the order of the deck.
 *
 * [deck] (all 50 cards: 5 for the first player, 5 for the second, then the deck from the top) exists so tests can choose
 * the cards; otherwise the deck is shuffled with [random], a SecureRandom in rooms.
 */
class Hanabi(
    val first: String,
    val second: String,
    deck: List<Int>? = null,
    random: Random = SecureRandom(),
) : Game {
    enum class Status { PLAYING, WON, FINISHED, LOST }

    /** A card in a hand, and what its holder has been told about it. */
    private class Slot(val card: Int) {
        var knownColor = -1
        var knownNumber = 0
        val notColors = sortedSetOf<Int>()
        val notNumbers = sortedSetOf<Int>()

        /** The card as its holder sees it ([hidden]), with only what the hints told them, or as everybody else sees it. */
        fun view(hidden: Boolean) = HanabiCard(
            color = if (hidden) -1 else colorOf(card),
            number = if (hidden) 0 else numberOf(card),
            knownColor = knownColor,
            knownNumber = knownNumber,
            notColors = notColors.toList(),
            notNumbers = notNumbers.toList(),
        )
    }

    private val hands = mapOf(first to mutableListOf<Slot>(), second to mutableListOf())
    private val stock = ArrayDeque<Int>()

    /** The highest number played on each colour's firework, 0 for none yet. */
    private val fireworks = IntArray(COLORS.size)
    private val discards = mutableListOf<Int>()

    var hints = MAX_HINTS
        private set
    var lives = LIVES
        private set

    /** The player who acts now; after the end, the one who acted last. */
    var turn = first
        private set
    var status = Status.PLAYING
        private set

    /** Turns still to be played once the deck has run out (null until then). */
    private var turnsLeft: Int? = null

    /** What just happened, as a line written for the given player. */
    private var lastAction: (String) -> String = { "" }

    /** The cards the last action hinted, as positions in [hinted]'s hand; empty unless the last action was a hint. */
    private var lastHint: List<Int> = emptyList()
    private var hinted: String? = null

    init {
        val cards = deck ?: FULL_DECK.shuffled(random)
        require(cards.sorted() == FULL_DECK) { "the deck must hold the 50 Hanabi cards" }
        hands.getValue(first) += cards.subList(0, HAND).map(::Slot)
        hands.getValue(second) += cards.subList(HAND, 2 * HAND).map(::Slot)
        stock += cards.subList(2 * HAND, cards.size)
    }

    override val finished get() = status != Status.PLAYING

    /** The sum of the fireworks, 0 to 25. */
    val score get() = fireworks.sum()

    val deckSize get() = stock.size

    override fun handle(player: String, msg: ClientMessage): String? {
        if (msg.type != "move") return "Hanabi only understands moves"
        return when (msg.action) {
            "hint" ->
                if (msg.card != null) "A hint names a colour or a number, not a card" else hint(player, msg.color, msg.number)
            "discard", "play" -> when {
                msg.card == null -> "Send the card to ${msg.action}"
                msg.color != null || msg.number != null -> "Only hints name a colour or a number"
                msg.action == "play" -> play(player, msg.card)
                else -> discard(player, msg.card)
            }
            else -> "Send an action: hint, discard or play"
        }
    }

    /**
     * [player] hints [color] (0..4) or [number] (1..5), exactly one of them, to their partner. Returns an error message,
     * or null if the hint was given.
     */
    fun hint(player: String, color: Int?, number: Int?): String? {
        refusal(player)?.let { return it }
        if ((color == null) == (number == null)) return "Hint one colour or one number"
        if (color != null && color !in COLORS.indices) return "Invalid colour"
        if (number != null && number !in 1..MAX_NUMBER) return "Invalid number"
        if (hints == 0) return "No hint tokens left"
        val partner = other(player)
        val hand = hands.getValue(partner)
        fun named(slot: Slot) = if (color != null) colorOf(slot.card) == color else numberOf(slot.card) == number
        val pointed = hand.indices.filter { named(hand[it]) }
        if (pointed.isEmpty()) return "That hint points at none of $partner's cards"

        for (slot in hand) {
            when {
                color != null && named(slot) -> slot.knownColor = color
                color != null -> slot.notColors += color
                named(slot) -> slot.knownNumber = number!!
                else -> slot.notNumbers += number!!
            }
            // Ruling out all but one is as good as being told.
            if (slot.knownColor < 0 && slot.notColors.size == COLORS.size - 1) {
                slot.knownColor = COLORS.indices.first { it !in slot.notColors }
            }
            if (slot.knownNumber == 0 && slot.notNumbers.size == MAX_NUMBER - 1) {
                slot.knownNumber = (1..MAX_NUMBER).first { it !in slot.notNumbers }
            }
        }
        hints--
        val what = if (color != null) "${COLORS[color]} cards" else "${number}s"
        lastAction = { viewer -> "${subject(player, viewer)} hinted ${if (viewer == partner) "your" else "$partner's"} $what" }
        lastHint = pointed
        hinted = partner
        endTurn()
        return null
    }

    /** [player] discards the card at [index] in their hand and draws. Returns an error message, or null if it was done. */
    fun discard(player: String, index: Int): String? {
        refusal(player)?.let { return it }
        val hand = hands.getValue(player)
        if (index !in hand.indices) return "Invalid card"
        if (hints == MAX_HINTS) return "All $MAX_HINTS hint tokens are there: give a hint or play instead"

        val card = hand.removeAt(index).card
        discards += card
        hints++
        draw(player)
        lastAction = { viewer -> "${subject(player, viewer)} discarded a ${nameOf(card)}" }
        clearHint()
        endTurn()
        return null
    }

    /** [player] plays the card at [index] in their hand and draws. Returns an error message, or null if it was played. */
    fun play(player: String, index: Int): String? {
        refusal(player)?.let { return it }
        val hand = hands.getValue(player)
        if (index !in hand.indices) return "Invalid card"

        val card = hand.removeAt(index).card
        val color = colorOf(card)
        val number = numberOf(card)
        if (fireworks[color] == number - 1) {
            fireworks[color] = number
            val bonus = number == MAX_NUMBER && hints < MAX_HINTS
            if (bonus) hints++
            val extra = if (bonus) ", and a hint token came back" else ""
            lastAction = { viewer -> "${subject(player, viewer)} played a ${nameOf(card)}$extra" }
            if (score == MAX_SCORE) status = Status.WON
        } else {
            discards += card
            lives--
            val left = when (lives) {
                0 -> "no lives left"
                1 -> "1 life left"
                else -> "$lives lives left"
            }
            lastAction = { viewer -> "${subject(player, viewer)} misplayed a ${nameOf(card)}: $left" }
            if (lives == 0) status = Status.LOST
        }
        if (status == Status.PLAYING) draw(player)
        clearHint()
        endTurn()
        return null
    }

    private fun draw(player: String) {
        stock.removeFirstOrNull()?.let { hands.getValue(player) += Slot(it) }
    }

    private fun clearHint() {
        lastHint = emptyList()
        hinted = null
    }

    /** Passes the turn, counting down the last round once the deck is empty. */
    private fun endTurn() {
        if (status != Status.PLAYING) return
        val left = turnsLeft
        if (left != null) {
            turnsLeft = left - 1
            if (left - 1 == 0) {
                status = Status.FINISHED
                return
            }
        } else if (stock.isEmpty()) {
            // This turn drew the last card: the partner plays once more, and then this player.
            turnsLeft = 2
        }
        turn = other(turn)
    }

    /** Why [player] may not act now, or null if they may. */
    private fun refusal(player: String) = when {
        status != Status.PLAYING -> "The game is over"
        player != first && player != second -> "You are not in this game"
        player != turn -> "Not your turn"
        else -> null
    }

    private fun other(player: String) = if (player == first) second else first

    /** "You" for the viewer themselves, otherwise the player's name. */
    private fun subject(player: String, viewer: String) = if (player == viewer) "You" else player

    private fun result(): String = when (status) {
        Status.PLAYING -> ""
        Status.WON -> "A perfect show: all five fireworks complete, 25 out of 25"
        Status.FINISHED -> "The deck ran out: $score out of 25"
        Status.LOST -> "The third misplay ended the show: you both lost, with $score of 25 on the fireworks"
    }

    override fun messageFor(player: String) =
        ServerMessage(type = "game", from = "server", text = describe(), hanabi = toState(player))

    /** The game as [player] sees it: their partner's cards, and only what they have been told about their own. */
    fun toState(player: String): HanabiState {
        val partner = other(player)
        return HanabiState(
            game = ID,
            you = player,
            partner = partner,
            firstPlayer = first,
            yourHand = hands.getValue(player).map { it.view(hidden = true) },
            partnerHand = hands.getValue(partner).map { it.view(hidden = false) },
            fireworks = fireworks.toList(),
            discards = discards.map { HanabiCard(color = colorOf(it), number = numberOf(it)) },
            hints = hints,
            lives = lives,
            deckSize = stock.size,
            yourTurn = status == Status.PLAYING && turn == player,
            turnsLeft = turnsLeft ?: -1,
            status = status.name.lowercase(),
            score = score,
            lastAction = lastAction(player),
            hintedYours = if (hinted == player) lastHint else emptyList(),
            hintedPartners = if (hinted == partner) lastHint else emptyList(),
            result = result(),
        )
    }

    /** Short human-readable summary, for clients that only show text. It shows nobody's cards. */
    fun describe(): String {
        val table = "score $score, $hints hints, ${if (lives == 1) "1 life" else "$lives lives"}, $deckSize cards left"
        return when (status) {
            Status.PLAYING -> "Hanabi: $turn to play, $table"
            else -> "Hanabi: ${result()}"
        }
    }

    companion object {
        const val ID = "hanabi"

        val COLORS = listOf("red", "yellow", "green", "blue", "white")
        const val MAX_NUMBER = 5
        const val HAND = 5
        const val MAX_HINTS = 8
        const val LIVES = 3
        const val MAX_SCORE = 25

        /** How many cards of each number there are in each colour: three 1s, two 2s, two 3s, two 4s and one 5. */
        val COPIES = listOf(3, 2, 2, 2, 1)

        fun colorOf(card: Int) = card / MAX_NUMBER
        fun numberOf(card: Int) = card % MAX_NUMBER + 1
        fun cardOf(color: Int, number: Int) = color * MAX_NUMBER + number - 1

        /** The card's name, e.g. "red 3". */
        fun nameOf(card: Int) = "${COLORS[colorOf(card)]} ${numberOf(card)}"

        /** All 50 cards, smallest first. */
        val FULL_DECK: List<Int> = COLORS.indices.flatMap { color ->
            (1..MAX_NUMBER).flatMap { number -> List(COPIES[number - 1]) { cardOf(color, number) } }
        }
    }
}

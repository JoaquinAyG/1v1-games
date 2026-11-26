package com.classicgames.core

import java.security.SecureRandom
import java.util.Random

/**
 * Brisca for two players, with the 40-card Spanish deck: the suits Oros, Copas, Espadas and Bastos, and in each the
 * As, 2 to 7, Sota, Caballo and Rey. Cards are numbered as described on [BriscaState].
 *
 * Each player gets 3 cards, and the next card is turned face up: its suit is trumps, and it lies under the deck as the
 * last card to be drawn. [first] leads the first trick. In a trick the leader plays a card and then the other player
 * plays one; nobody has to follow suit or beat it. The highest trump wins the trick, otherwise the highest card of the
 * suit led, in the order As, 3, Rey, Caballo, Sota, 7, 6, 5, 4, 2. The winner takes both cards and their points (As 11,
 * 3 10, Rey 4, Caballo 3, Sota 2, the rest nothing: 120 in all), draws first (the other player draws next, so the
 * face-up trump goes to the loser of the trick that empties the deck), and leads the next trick. Once the deck is
 * empty the hands are played out. With 61 points or more you win; 60 each is a draw.
 *
 * Swapping the 7: while the face-up trump is still under the deck, the player who holds the 7 of trumps may, on their
 * turn and before playing their card, swap it for the face-up trump. That is allowed whenever those conditions hold,
 * as the leader or as the second player of a trick, whatever the face-up card is.
 *
 * Each player only ever sees their own hand: the opponent's hand and the order of the deck are never sent.
 *
 * [deck] (all 40 cards: 3 for the first player, 3 for the second, the card turned up, then the deck from the top)
 * exists so tests can choose the cards; otherwise the deck is shuffled with [random], a SecureRandom in rooms.
 */
class Brisca(
    val first: String,
    val second: String,
    deck: List<Int>? = null,
    random: Random = SecureRandom(),
) : Game {
    enum class Status { PLAYING, WON, DRAW }

    private val hands = mapOf(first to mutableListOf<Int>(), second to mutableListOf())
    private val stock = ArrayDeque<Int>()

    /** The card turned face up under the deck (after a swap, the 7 of trumps). It decides the trump suit. */
    var trumpCard: Int
        private set

    /** True until the face-up trump has been drawn as the deck's last card. */
    private var trumpUnderDeck = true

    private val trick = mutableListOf<BriscaPlay>()
    private var lastTrick: List<BriscaPlay> = emptyList()
    private var lastTrickWinner: String? = null
    private val points = mutableMapOf(first to 0, second to 0)

    /** The player who must play (or swap) now; after the end, whoever won the last trick. */
    var turn = first
        private set
    var status = Status.PLAYING
        private set
    var winner: String? = null
        private set

    /** What just happened, as a sentence written for the given player. */
    private var event: (String) -> String = { "" }

    init {
        val cards = deck ?: (0 until CARDS).toList().shuffled(random)
        require(cards.size == CARDS && cards.toSet() == (0 until CARDS).toSet()) { "the deck must hold every card 0 until $CARDS once" }
        hands.getValue(first) += cards.subList(0, HAND)
        hands.getValue(second) += cards.subList(HAND, 2 * HAND)
        trumpCard = cards[2 * HAND]
        stock += cards.subList(2 * HAND + 1, CARDS)
    }

    override val finished get() = status != Status.PLAYING

    val trumpSuit get() = suitOf(trumpCard)

    /** Cards still to be drawn, the face-up trump included while it is under the deck. */
    val deckSize get() = stock.size + if (trumpUnderDeck) 1 else 0

    /** True when [player] may swap the 7 of trumps for the face-up trump now. */
    fun canSwap(player: String) =
        status == Status.PLAYING && turn == player && trumpUnderDeck && seven() in hands.getValue(player)

    override fun handle(player: String, msg: ClientMessage): String? = when (msg.type) {
        "move" -> {
            val card = msg.card
            if (card == null || msg.cell != null || msg.piece != null) "Send the card to play" else play(player, card)
        }
        "swap7" -> swapSeven(player)
        else -> "Brisca understands 'move' and 'swap7'"
    }

    /** [player] plays [card] from their hand. Returns an error message, or null if it was played. */
    fun play(player: String, card: Int): String? {
        refusal(player)?.let { return it }
        if (card !in 0 until CARDS) return "Invalid card"
        val hand = hands.getValue(player)
        if (card !in hand) return "That card is not in your hand"

        hand.remove(card)
        trick += BriscaPlay(player, card)
        if (trick.size == 1) {
            turn = other(player)
            event = { "" }
            return null
        }

        // The trick is complete: the winner takes it, draws first and leads the next one.
        val (lead, follow) = trick
        val taker = if (leadWins(lead.card, follow.card, trumpSuit)) lead.player else follow.player
        val won = pointsOf(lead.card) + pointsOf(follow.card)
        points[taker] = points.getValue(taker) + won
        lastTrick = trick.toList()
        lastTrickWinner = taker
        trick.clear()
        turn = taker

        val emptied = deckSize > 0 && deckSize <= 2
        if (deckSize > 0) for (p in listOf(taker, other(taker))) hands.getValue(p) += draw()

        if (hands.values.all { it.isEmpty() }) {
            finish()
        } else {
            val score = if (won == 0) "no points" else "$won points"
            val last = if (emptied) ". The deck is empty" else ""
            event = { viewer -> "${if (viewer == taker) "You" else taker} took the trick, $score$last" }
        }
        return null
    }

    /** [player] swaps the 7 of trumps for the face-up trump. Returns an error message, or null if it was done. */
    fun swapSeven(player: String): String? {
        refusal(player)?.let { return it }
        if (!trumpUnderDeck) return "The face-up trump has already been drawn"
        val hand = hands.getValue(player)
        val seven = seven()
        val index = hand.indexOf(seven)
        if (index < 0) return "You don't have the 7 of trumps"

        val taken = trumpCard
        hand[index] = taken
        trumpCard = seven
        event = { viewer ->
            if (viewer == player) "You swapped the ${nameOf(seven)} for the ${nameOf(taken)}"
            else "$player swapped the ${nameOf(seven)} for the ${nameOf(taken)}"
        }
        return null
    }

    /** The next card from the deck: the top one, or the face-up trump once nothing else is left. */
    private fun draw(): Int = stock.removeFirstOrNull() ?: trumpCard.also { trumpUnderDeck = false }

    private fun finish() {
        val one = points.getValue(first)
        val two = points.getValue(second)
        when {
            one >= WINNING_POINTS -> { status = Status.WON; winner = first }
            two >= WINNING_POINTS -> { status = Status.WON; winner = second }
            else -> status = Status.DRAW
        }
        event = { viewer ->
            val mine = points.getValue(viewer)
            val theirs = points.getValue(other(viewer))
            when (winner) {
                null -> "A draw, $mine-$theirs"
                viewer -> "You won $mine-$theirs"
                else -> "$winner won $theirs-$mine"
            }
        }
    }

    /** Why [player] may not play now, or null if they may. */
    private fun refusal(player: String) = when {
        status != Status.PLAYING -> "The game is over"
        player != first && player != second -> "You are not in this game"
        player != turn -> "Not your turn"
        else -> null
    }

    private fun seven() = cardOf(trumpSuit, 6)
    private fun other(player: String) = if (player == first) second else first

    override fun messageFor(player: String) =
        ServerMessage(type = "game", from = "server", text = describe(), brisca = toState(player))

    /** The game as [player] sees it: their own hand, and only the size of the opponent's. */
    fun toState(player: String): BriscaState {
        val opponent = other(player)
        return BriscaState(
            game = ID,
            you = player,
            opponent = opponent,
            firstPlayer = first,
            hand = hands.getValue(player).toList(),
            opponentCards = hands.getValue(opponent).size,
            deckSize = deckSize,
            trumpCard = trumpCard,
            trick = trick.toList(),
            lastTrick = lastTrick,
            lastTrickWinner = lastTrickWinner,
            yourPoints = points.getValue(player),
            opponentPoints = points.getValue(opponent),
            yourTurn = status == Status.PLAYING && turn == player,
            canSwap = canSwap(player),
            status = status.name.lowercase(),
            winner = winner,
            event = event(player),
        )
    }

    /** Short human-readable summary, for clients that only show text. It shows nobody's cards. */
    fun describe(): String {
        val score = "$first ${points.getValue(first)}, $second ${points.getValue(second)}"
        return when (status) {
            Status.WON -> "Brisca: $winner won ($score)"
            Status.DRAW -> "Brisca: draw ($score)"
            Status.PLAYING -> "Brisca: $turn to play, trumps ${SUITS[trumpSuit]} ($score)"
        }
    }

    companion object {
        const val ID = "brisca"

        const val CARDS = 40
        const val HAND = 3
        const val WINNING_POINTS = 61

        val SUITS = listOf("Oros", "Copas", "Espadas", "Bastos")

        /** Rank names by rank number 0..9. */
        val RANKS = listOf("As", "2", "3", "4", "5", "6", "7", "Sota", "Caballo", "Rey")

        /** Points by rank number: As 11, 3 10, Sota 2, Caballo 3, Rey 4. */
        private val POINTS = listOf(11, 0, 10, 0, 0, 0, 0, 2, 3, 4)

        /** Strength within a suit by rank number, higher wins: As > 3 > Rey > Caballo > Sota > 7 > 6 > 5 > 4 > 2. */
        private val POWER = listOf(9, 0, 8, 1, 2, 3, 4, 5, 6, 7)

        fun suitOf(card: Int) = card / 10
        fun rankOf(card: Int) = card % 10
        fun cardOf(suit: Int, rank: Int) = suit * 10 + rank
        fun pointsOf(card: Int) = POINTS[rankOf(card)]

        /** The card's Spanish name, e.g. "As de Oros" or "Caballo de Bastos". */
        fun nameOf(card: Int) = "${RANKS[rankOf(card)]} de ${SUITS[suitOf(card)]}"

        /** True if [lead], played first, beats [follow] when [trump] is the trump suit. */
        fun leadWins(lead: Int, follow: Int, trump: Int): Boolean = when (suitOf(follow)) {
            suitOf(lead) -> POWER[rankOf(lead)] > POWER[rankOf(follow)]
            trump -> false
            else -> true
        }
    }
}

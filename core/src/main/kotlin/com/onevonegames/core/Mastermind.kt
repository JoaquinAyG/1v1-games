package com.onevonegames.core

/**
 * A Mastermind duel. Each player first sets a secret code of [pegs] pegs, each one of [colors] colours (repeats
 * allowed). Then the players take turns guessing the opponent's code, [first] guessing first. Every guess is scored
 * with black pegs (right colour in the right place) and white pegs (right colour in the wrong place).
 *
 * The game is played in rounds of one guess each, so both players always get the same number of guesses:
 *  - If [first] cracks the code, [second] still gets their guess in that round. Both cracking it in the same round
 *    is a draw; otherwise whoever cracked it wins.
 *  - If nobody has cracked a code after [maxGuesses] guesses each, it's a draw.
 * Both codes are revealed once the game is over, and never before: each player gets their own view.
 *
 * Colours are numbered 0 until [colors]. [pegs], [colors] and [maxGuesses] exist so tests can play shorter games;
 * rooms always use [PEGS], [COLORS] and [MAX_GUESSES].
 */
class Mastermind(
    val first: String,
    val second: String,
    val pegs: Int = PEGS,
    val colors: Int = COLORS,
    val maxGuesses: Int = MAX_GUESSES,
) : Game {
    enum class Phase { SECRET, GUESSING, FINISHED }

    init {
        require(pegs >= 1 && colors >= 2 && maxGuesses >= 1) { "the game needs pegs, colours and guesses" }
    }

    private class Player {
        /** The code this player chose, which the opponent tries to crack. */
        var secret: List<Int>? = null

        /** This player's guesses at the opponent's code, oldest first. */
        val guesses = ArrayList<MastermindGuess>()

        val cracked get() = guesses.lastOrNull()?.let { it.black == it.code.size } == true
    }

    var phase = Phase.SECRET
        private set

    /** Whose turn it is to guess (only meaningful while guessing). */
    var turn: String = first
        private set

    /** The winner, or null while playing and after a draw. */
    var winner: String? = null
        private set

    private val players = mapOf(first to Player(), second to Player())

    override val finished get() = phase == Phase.FINISHED

    private fun other(player: String) = if (player == first) second else first

    override fun handle(player: String, msg: ClientMessage): String? {
        if (player !in players) return "You are not in this game"
        return when (msg.type) {
            "secret" -> setSecret(player, msg.code)
            "move" -> guess(player, msg.code)
            else -> "Mastermind understands 'secret' and 'move'"
        }
    }

    /** Sets [player]'s secret [code], once. Returns an error message, or null if it was accepted. */
    fun setSecret(player: String, code: List<Int>?): String? {
        val me = players[player] ?: return "You are not in this game"
        if (phase != Phase.SECRET) return "The codes are already set"
        if (me.secret != null) return "You already set your code"
        validate(code)?.let { return it }

        me.secret = code!!.toList()
        if (players.values.all { it.secret != null }) {
            phase = Phase.GUESSING
            turn = first
        }
        return null
    }

    /** Scores [player]'s guess at the opponent's code. Returns an error message, or null if it was accepted. */
    fun guess(player: String, code: List<Int>?): String? {
        val me = players[player] ?: return "You are not in this game"
        when (phase) {
            Phase.SECRET -> return if (me.secret == null) "Set your code first" else "Wait for ${other(player)} to set their code"
            Phase.FINISHED -> return "The game is over"
            Phase.GUESSING -> {}
        }
        if (player != turn) return "Not your turn"
        validate(code)?.let { return it }

        val (black, white) = score(players.getValue(other(player)).secret!!, code!!)
        me.guesses += MastermindGuess(code.toList(), black, white)

        if (player == first) {
            // The second player always answers in the same round, even when the first one just cracked the code.
            turn = second
            return null
        }
        val firstCracked = players.getValue(first).cracked
        when {
            firstCracked && me.cracked -> phase = Phase.FINISHED
            firstCracked -> { phase = Phase.FINISHED; winner = first }
            me.cracked -> { phase = Phase.FINISHED; winner = second }
            me.guesses.size >= maxGuesses -> phase = Phase.FINISHED
            else -> turn = first
        }
        return null
    }

    private fun validate(code: List<Int>?): String? {
        if (code == null || code.size != pegs || code.any { it !in 0 until colors }) {
            return "A code is $pegs pegs, each one of the $colors colours"
        }
        return null
    }

    override fun messageFor(player: String): ServerMessage {
        val me = players.getValue(player)
        val opponent = players.getValue(other(player))
        val state = MastermindState(
            game = ID,
            phase = phase.name.lowercase(),
            you = player,
            opponent = other(player),
            firstPlayer = first,
            pegs = pegs,
            colors = colors,
            maxGuesses = maxGuesses,
            youReady = me.secret != null,
            opponentReady = opponent.secret != null,
            yourTurn = phase == Phase.GUESSING && turn == player,
            yourSecret = me.secret.orEmpty(),
            // The opponent's code only once the game is over.
            opponentSecret = if (finished) opponent.secret.orEmpty() else emptyList(),
            yourGuesses = me.guesses.toList(),
            opponentGuesses = opponent.guesses.toList(),
            winner = winner,
            event = describe(player),
        )
        return ServerMessage(type = "game", from = "server", text = state.event, mastermind = state)
    }

    /** One sentence about the latest happening, from [player]'s point of view. */
    private fun describe(player: String): String {
        val opponent = other(player)
        val me = players.getValue(player)
        val them = players.getValue(opponent)
        return when (phase) {
            Phase.SECRET -> when {
                me.secret != null -> "Waiting for $opponent to set their code"
                them.secret != null -> "$opponent has set their code. Set yours!"
                else -> "Set your secret code"
            }
            Phase.FINISHED -> {
                val rounds = players.getValue(second).guesses.size
                val guesses = if (rounds == 1) "1 guess" else "$rounds guesses"
                when (winner) {
                    null -> if (me.cracked) "Both codes cracked in $guesses: it's a draw"
                    else "Nobody cracked a code in $maxGuesses guesses: it's a draw"
                    player -> "You cracked $opponent's code in $guesses!"
                    else -> "$opponent cracked your code in $guesses"
                }
            }
            Phase.GUESSING -> {
                val latest = if (turn == player) them.guesses.lastOrNull() else me.guesses.lastOrNull()
                when {
                    players.getValue(first).cracked -> if (player == first) {
                        "You cracked $opponent's code! $opponent has one guess left to draw"
                    } else {
                        "$opponent cracked your code! Crack theirs with this guess to draw"
                    }
                    latest == null -> if (turn == player) "Both codes are set. You guess first!"
                    else "Both codes are set. $opponent guesses first"
                    turn == player -> "$opponent scored ${latest.black} black, ${latest.white} white. Your turn"
                    else -> "You scored ${latest.black} black, ${latest.white} white"
                }
            }
        }
    }

    companion object {
        const val ID = "mastermind"

        /** Pegs in a code. */
        const val PEGS = 4

        /** Colours a peg can have, numbered 0 until COLORS. */
        const val COLORS = 6

        /** Guesses each player gets. */
        const val MAX_GUESSES = 10

        /**
         * Scores [guess] against [secret]: black pegs for the right colour in the right place, and white pegs for
         * the right colour in the wrong place. Each peg of either code counts at most once, so a colour repeated
         * in the guess only scores as often as it appears in the secret.
         */
        fun score(secret: List<Int>, guess: List<Int>): Pair<Int, Int> {
            require(secret.size == guess.size) { "codes of different lengths" }
            val black = secret.indices.count { secret[it] == guess[it] }
            val common = (secret + guess).toSet().sumOf { color ->
                minOf(secret.count { it == color }, guess.count { it == color })
            }
            return black to common - black
        }
    }
}

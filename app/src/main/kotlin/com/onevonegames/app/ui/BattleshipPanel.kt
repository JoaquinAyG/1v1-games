package com.onevonegames.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onevonegames.core.BattleState
import kotlin.random.Random

private const val SIZE = 10

/** Ship sizes and names, biggest first. Mirrors the fleet on the server. */
private val FLEET = listOf(5, 4, 3, 3, 2)
private val SHIP_NAMES = listOf("Carrier", "Battleship", "Cruiser", "Submarine", "Destroyer")

private val WaterColor = Color(0xFFD6E9F8)
private val ShipColor = Color(0xFF546E7A)
private val HitColor = Color(0xFFE5484D)
private val SunkColor = Color(0xFF8E1B21)
private val MissColor = Color(0xFF8FB2D6)

/**
 * Everything for one Battleship game: placing the fleet, then firing at the enemy.
 * The server decides everything; this only shows its state and reports taps.
 */
@Composable
fun BattleshipPanel(
    battle: BattleState,
    notice: String?,
    onPlaceFleet: (List<List<Int>>) -> Unit,
    onFire: (Int) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            battle.phase == "placing" && !battle.youReady -> PlacementEditor(battle, onPlaceFleet)
            battle.phase == "placing" -> {
                Text(battle.event, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text("Your fleet", style = MaterialTheme.typography.labelLarge)
                SeaBoard(battle.myBoard)
            }
            else -> BattleView(battle, onFire, onPlayAgain)
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun PlacementEditor(battle: BattleState, onPlaceFleet: (List<List<Int>>) -> Unit) {
    var placed by remember { mutableStateOf<Map<Int, List<Int>>>(emptyMap()) } // ship index -> its cells
    var selected by remember { mutableIntStateOf(0) }
    var horizontal by remember { mutableStateOf(true) }
    var problem by remember { mutableStateOf<String?>(null) }

    val board = remember(placed) {
        val cells = CharArray(SIZE * SIZE) { '.' }
        placed.values.forEach { ship -> ship.forEach { cells[it] = 'S' } }
        String(cells)
    }

    /** Puts the selected ship with its first cell on [cell], going right or down. */
    fun tryPlace(cell: Int) {
        val size = FLEET[selected]
        val fits = if (horizontal) cell % SIZE + size <= SIZE else cell / SIZE + size <= SIZE
        val cells = List(size) { if (horizontal) cell + it else cell + it * SIZE }
        val others = placed.filterKeys { it != selected }.values.flatten().toSet()
        when {
            !fits -> problem = "That ship doesn't fit there"
            cells.any { it in others } -> problem = "Ships can't overlap"
            else -> {
                problem = null
                val next = placed + (selected to cells)
                placed = next
                // Move on to the next ship that still needs a place.
                selected = FLEET.indices.firstOrNull { it !in next && it > selected }
                    ?: FLEET.indices.firstOrNull { it !in next }
                    ?: selected
            }
        }
    }

    Text(battle.event, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Text(
        "Pick a ship, then tap the board to place it. Tap again to move it.",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FLEET.indices.forEach { i ->
            FilterChip(
                selected = selected == i,
                onClick = { selected = i },
                label = { Text("${SHIP_NAMES[i]} (${FLEET[i]})") },
                leadingIcon = if (i in placed) ({ Text("\u2713") }) else null,
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { horizontal = !horizontal }) {
            Text(if (horizontal) "Direction: \u2192" else "Direction: \u2193")
        }
        OutlinedButton(onClick = { placed = randomFleet(); problem = null }) { Text("Random") }
        OutlinedButton(onClick = { placed = emptyMap(); selected = 0; problem = null }) { Text("Reset") }
    }

    SeaBoard(board, onCell = ::tryPlace)

    problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

    Button(
        onClick = { onPlaceFleet(FLEET.indices.map { placed.getValue(it) }) },
        enabled = placed.size == FLEET.size,
    ) { Text("Ready") }
}

@Composable
private fun BattleView(battle: BattleState, onFire: (Int) -> Unit, onPlayAgain: () -> Unit) {
    val finished = battle.phase == "finished"
    Text(
        when {
            finished -> if (battle.winner == battle.you) "You won!" else "${battle.winner} won"
            battle.yourTurn -> "Your turn: fire!"
            else -> "${battle.opponent}'s turn..."
        },
        style = MaterialTheme.typography.titleMedium,
    )
    Text(battle.event, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)

    Text("Enemy waters", style = MaterialTheme.typography.labelLarge)
    SeaBoard(
        cells = battle.shots,
        onCell = if (battle.yourTurn) onFire else null,
        canTap = { battle.shots[it] == '.' },
    )
    Text("Enemy ships afloat: ${fleetText(battle.enemyShipsLeft)}", style = MaterialTheme.typography.bodySmall)

    Text("Your fleet", style = MaterialTheme.typography.labelLarge)
    SeaBoard(cells = battle.myBoard, modifier = Modifier.fillMaxWidth(0.62f))
    Text("Your ships afloat: ${fleetText(battle.myShipsLeft)}", style = MaterialTheme.typography.bodySmall)

    if (finished) {
        Button(onClick = onPlayAgain) { Text("Play again") }
    }
}

private fun fleetText(sizes: List<Int>) = if (sizes.isEmpty()) "none" else sizes.joinToString(" ")

/**
 * A 10x10 board with column letters and row numbers.
 * [cells] has 100 characters (see BattleState); taps are reported through [onCell] for cells where [canTap] is true.
 */
@Composable
private fun SeaBoard(
    cells: String,
    modifier: Modifier = Modifier,
    onCell: ((Int) -> Unit)? = null,
    canTap: (Int) -> Boolean = { true },
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f))
            for (c in 0 until SIZE) BoardLabel('A' + c, Modifier.weight(1f))
        }
        for (r in 0 until SIZE) {
            Row(Modifier.fillMaxWidth()) {
                BoardLabel(r + 1, Modifier.weight(1f))
                for (c in 0 until SIZE) {
                    val i = r * SIZE + c
                    SeaCell(
                        mark = cells[i],
                        enabled = onCell != null && canTap(i),
                        onClick = { onCell?.invoke(i) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardLabel(label: Any, modifier: Modifier) {
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Text(label.toString(), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SeaCell(mark: Char, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val (color, label) = when (mark) {
        'S' -> ShipColor to ""
        'H' -> HitColor to "\u2715"
        'X' -> SunkColor to "\u2715"
        'M' -> MissColor to "\u2022"
        else -> WaterColor to ""
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(0.5.dp)
            .background(color)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) {
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** A random valid fleet: ship index -> its cells. */
private fun randomFleet(): Map<Int, List<Int>> {
    while (true) {
        val used = HashSet<Int>()
        val result = HashMap<Int, List<Int>>()
        var ok = true
        for ((i, size) in FLEET.withIndex()) {
            var placedThis = false
            var attempts = 0
            while (!placedThis && attempts++ < 200) {
                val horizontal = Random.nextBoolean()
                val row = Random.nextInt(if (horizontal) SIZE else SIZE - size + 1)
                val col = Random.nextInt(if (horizontal) SIZE - size + 1 else SIZE)
                val cells = List(size) { if (horizontal) row * SIZE + col + it else (row + it) * SIZE + col }
                if (cells.none { it in used }) {
                    used += cells
                    result[i] = cells
                    placedThis = true
                }
            }
            if (!placedThis) { ok = false; break }
        }
        if (ok) return result
    }
}

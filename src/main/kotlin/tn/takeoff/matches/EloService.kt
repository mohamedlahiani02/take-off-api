package tn.takeoff.matches

import org.springframework.stereotype.Service
import kotlin.math.roundToInt

@Service
class EloService {

    fun computeDelta(myLevel: Int, opponentLevel: Double, result: String): Int {
        val base = if (result == "W") 20.0 else -15.0
        val diff = opponentLevel - myLevel
        val delta = (base * (1 + 0.15 * diff)).roundToInt()
        return delta.coerceIn(-40, 40)
    }

    fun pointsToLevel(points: Int): Int = minOf(7, points / 150 + 1)
}

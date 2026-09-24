package tn.takeoff.courts

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tn.takeoff.common.errors.BadRequestException
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * What a padel booking costs. Configuration is the only authority.
 *
 * The price used to be a constant inside the member controller, which meant the
 * club could not change it without a rebuild and the browser had to assume the
 * amounts. Both the booking path and the public pricing endpoint resolve
 * through here, so a quoted price and a charged price cannot diverge.
 */
@Component
class CourtPricing(
    @Value("\${takeoff.courts.full-price-dt:80.000}") private val fullPriceDt: BigDecimal,
    @Value("\${takeoff.courts.seats-per-court:4}") val seatsPerCourt: Int,
) {
    init {
        if (fullPriceDt <= BigDecimal.ZERO)
            throw IllegalStateException("takeoff.courts.full-price-dt must be positive")
        if (seatsPerCourt < 1)
            throw IllegalStateException("takeoff.courts.seats-per-court must be at least 1")
    }

    /** Whole court: the organiser covers the booking outright. */
    fun fullPrice(): BigDecimal = fullPriceDt.setScale(3, RoundingMode.HALF_UP)

    /** One seat of four. The organiser pays only their own place. */
    fun seatPrice(): BigDecimal =
        fullPriceDt.divide(BigDecimal(seatsPerCourt), 3, RoundingMode.HALF_UP)

    /** The amount the booking member owes for the mode they picked. */
    fun priceFor(mode: BookingMode): BigDecimal =
        if (mode == BookingMode.SHARE) seatPrice() else fullPrice()

    /** Guards a client-supplied amount against the authoritative one. */
    fun requireMatches(mode: BookingMode, quoted: BigDecimal?) {
        if (quoted == null) return
        if (quoted.compareTo(priceFor(mode)) != 0) {
            throw BadRequestException(
                "takeoff.court.price_mismatch",
                "Price has changed — please review the booking before confirming",
            )
        }
    }
}

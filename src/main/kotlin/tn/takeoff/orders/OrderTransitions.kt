package tn.takeoff.orders

/**
 * The authoritative fulfilment state machine for an order.
 *
 * Kept separate from [OrderService] so the rules can be reasoned about — and tested — without a
 * database. CANCELLED, DELIVERED and PICKED_UP are terminal; nothing may move out of them.
 * Allowing a cancelled order to be re-confirmed is what let `cancel -> confirm -> cancel` pay a
 * second refund out of the same order.
 */
object OrderTransitions {

    private val GRAPH: Map<OrderStatus, Set<OrderStatus>> = mapOf(
        OrderStatus.PENDING to setOf(OrderStatus.CONFIRMED),
        OrderStatus.CONFIRMED to setOf(OrderStatus.PREPARING),
        OrderStatus.PREPARING to setOf(OrderStatus.SHIPPED, OrderStatus.PICKUP_READY),
        OrderStatus.SHIPPED to setOf(OrderStatus.DELIVERED),
        OrderStatus.PICKUP_READY to setOf(OrderStatus.PICKED_UP),
        OrderStatus.DELIVERED to emptySet(),
        OrderStatus.PICKED_UP to emptySet(),
        OrderStatus.CANCELLED to emptySet(),
    )

    /** Cancellation is only meaningful before the goods have left the club. */
    private val CANCELLABLE: Set<OrderStatus> = setOf(
        OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PREPARING,
        OrderStatus.SHIPPED, OrderStatus.PICKUP_READY,
    )

    /** Statuses an order may never leave. */
    val TERMINAL: Set<OrderStatus> = GRAPH.filterValues { it.isEmpty() }.keys

    fun allows(from: OrderStatus, to: OrderStatus): Boolean = to in (GRAPH[from] ?: emptySet())

    fun canCancel(from: OrderStatus): Boolean = from in CANCELLABLE

    /** Maps an admin action verb to its target status, or null when the verb is unknown. */
    fun statusForAction(action: String): OrderStatus? = when (action.lowercase()) {
        "confirm" -> OrderStatus.CONFIRMED
        "prepare" -> OrderStatus.PREPARING
        "ship" -> OrderStatus.SHIPPED
        "deliver" -> OrderStatus.DELIVERED
        "pickup_ready" -> OrderStatus.PICKUP_READY
        "picked_up" -> OrderStatus.PICKED_UP
        else -> null
    }
}

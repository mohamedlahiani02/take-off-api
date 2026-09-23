package tn.takeoff.orders

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression cover for the money bug where `cancel -> confirm -> cancel` refunded a wallet order
 * twice. A row lock serialised concurrent requests but said nothing about a *sequential*
 * reopen, so the state machine itself has to forbid it.
 */
class OrderTransitionsTest {

    @Test
    fun `a cancelled order can never be reopened`() {
        for (target in OrderStatus.entries) {
            assertFalse(
                OrderTransitions.allows(OrderStatus.CANCELLED, target),
                "CANCELLED must not transition to $target",
            )
        }
    }

    @Test
    fun `a cancelled order cannot be cancelled again`() {
        assertFalse(OrderTransitions.canCancel(OrderStatus.CANCELLED))
    }

    @Test
    fun `completed orders are terminal`() {
        assertEquals(
            setOf(OrderStatus.CANCELLED, OrderStatus.DELIVERED, OrderStatus.PICKED_UP),
            OrderTransitions.TERMINAL,
        )
        for (terminal in OrderTransitions.TERMINAL) {
            for (target in OrderStatus.entries) {
                assertFalse(OrderTransitions.allows(terminal, target), "$terminal -> $target")
            }
        }
    }

    @Test
    fun `the happy fulfilment paths are allowed`() {
        assertTrue(OrderTransitions.allows(OrderStatus.PENDING, OrderStatus.CONFIRMED))
        assertTrue(OrderTransitions.allows(OrderStatus.CONFIRMED, OrderStatus.PREPARING))
        assertTrue(OrderTransitions.allows(OrderStatus.PREPARING, OrderStatus.SHIPPED))
        assertTrue(OrderTransitions.allows(OrderStatus.PREPARING, OrderStatus.PICKUP_READY))
        assertTrue(OrderTransitions.allows(OrderStatus.SHIPPED, OrderStatus.DELIVERED))
        assertTrue(OrderTransitions.allows(OrderStatus.PICKUP_READY, OrderStatus.PICKED_UP))
    }

    @Test
    fun `fulfilment cannot skip ahead or run backwards`() {
        assertFalse(OrderTransitions.allows(OrderStatus.PENDING, OrderStatus.SHIPPED))
        assertFalse(OrderTransitions.allows(OrderStatus.PENDING, OrderStatus.DELIVERED))
        assertFalse(OrderTransitions.allows(OrderStatus.SHIPPED, OrderStatus.PENDING))
        assertFalse(OrderTransitions.allows(OrderStatus.DELIVERED, OrderStatus.SHIPPED))
        // A pickup order must not be "shipped", nor a shipped order "picked up".
        assertFalse(OrderTransitions.allows(OrderStatus.PICKUP_READY, OrderStatus.DELIVERED))
        assertFalse(OrderTransitions.allows(OrderStatus.SHIPPED, OrderStatus.PICKED_UP))
    }

    @Test
    fun `orders can be cancelled until they leave the club`() {
        for (status in listOf(
            OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PREPARING,
            OrderStatus.SHIPPED, OrderStatus.PICKUP_READY,
        )) {
            assertTrue(OrderTransitions.canCancel(status), "$status should be cancellable")
        }
        assertFalse(OrderTransitions.canCancel(OrderStatus.DELIVERED))
        assertFalse(OrderTransitions.canCancel(OrderStatus.PICKED_UP))
    }

    @Test
    fun `unknown action verbs are not silently accepted`() {
        assertNull(OrderTransitions.statusForAction("refund"))
        assertNull(OrderTransitions.statusForAction(""))
        assertNull(OrderTransitions.statusForAction("cancel")) // handled on its own path
        assertEquals(OrderStatus.CONFIRMED, OrderTransitions.statusForAction("CONFIRM"))
    }
}

package tn.takeoff.payments

import java.util.Optional
import java.util.UUID

interface PaymentIntentGateway {
    fun save(intent: PaymentIntent): PaymentIntent
    fun findById(id: UUID): Optional<PaymentIntent>
    fun findByKonnectPayRef(ref: String): Optional<PaymentIntent>
}

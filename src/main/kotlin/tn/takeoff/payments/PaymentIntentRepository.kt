package tn.takeoff.payments

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface PaymentIntentRepository : JpaRepository<PaymentIntent, UUID>, PaymentIntentGateway {
    override fun findByKonnectPayRef(ref: String): Optional<PaymentIntent>
}

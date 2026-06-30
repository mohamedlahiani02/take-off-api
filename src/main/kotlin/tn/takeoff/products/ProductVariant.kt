package tn.takeoff.products

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

@Entity
@Table(name = "product_variants")
class ProductVariant(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "product_id", nullable = false) var productId: UUID,
    @Column(nullable = false) var size: String,
    @Column(nullable = false) var stock: Int = 0,
    @Column(name = "display_order", nullable = false) var displayOrder: Int = 0,
)

interface ProductVariantRepository : JpaRepository<ProductVariant, UUID> {
    fun findByProductIdOrderByDisplayOrder(productId: UUID): List<ProductVariant>
    fun deleteByProductId(productId: UUID)
}

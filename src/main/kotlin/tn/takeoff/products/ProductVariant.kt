package tn.takeoff.products

import jakarta.persistence.*
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM ProductVariant v WHERE v.productId = :productId ORDER BY v.displayOrder")
    fun findByProductIdForUpdate(@Param("productId") productId: UUID): List<ProductVariant>

    fun deleteByProductId(productId: UUID)
}

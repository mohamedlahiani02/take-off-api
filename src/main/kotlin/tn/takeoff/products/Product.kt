package tn.takeoff.products

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class ProductCategory {
    RACKETS, ACCESSORIES, PADELWEAR, PILATES, TOWELS, LIFESTYLE
}

@Entity
@Table(name = "products")
class Product(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var category: ProductCategory,

    @Column(name = "price_dt", precision = 10, scale = 3, nullable = false)
    var priceDt: BigDecimal,

    @Column(columnDefinition = "text")
    var description: String? = null,

    @Column(name = "has_sizes")
    var hasSizes: Boolean = false,

    var stock: Int = 0,

    var tag: String? = null,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "image_urls", columnDefinition = "text[]")
    var imageUrls: Array<String> = arrayOf(),

    @Column(name = "is_active")
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)

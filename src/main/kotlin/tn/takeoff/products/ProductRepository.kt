package tn.takeoff.products

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ProductRepository : JpaRepository<Product, UUID>, ProductGateway {

    @Query("""
        SELECT p FROM Product p
        WHERE p.isActive = true
          AND (:category IS NULL OR p.category = :category)
          AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    override fun search(
        category: ProductCategory?,
        search: String?,
        pageable: Pageable,
    ): Page<Product>
}

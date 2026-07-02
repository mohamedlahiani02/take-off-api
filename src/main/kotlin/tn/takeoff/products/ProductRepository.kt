package tn.takeoff.products

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ProductRepository : JpaRepository<Product, UUID>, ProductGateway {

    override fun findByIsActiveTrue(pageable: Pageable): Page<Product>

    override fun findByIsActiveTrueAndCategory(category: ProductCategory, pageable: Pageable): Page<Product>

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    override fun searchActive(search: String, pageable: Pageable): Page<Product>

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.category = :category AND LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    override fun searchActiveByCategory(category: ProductCategory, search: String, pageable: Pageable): Page<Product>
}

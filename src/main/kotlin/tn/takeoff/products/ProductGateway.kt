package tn.takeoff.products

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.Optional
import java.util.UUID

interface ProductGateway {
    fun findById(id: UUID): Optional<Product>
    fun findByIdForUpdate(id: UUID): Optional<Product>
    fun findByIsActiveTrue(pageable: Pageable): Page<Product>
    fun findByIsActiveTrueAndCategory(category: ProductCategory, pageable: Pageable): Page<Product>
    fun searchActive(search: String, pageable: Pageable): Page<Product>
    fun searchActiveByCategory(category: ProductCategory, search: String, pageable: Pageable): Page<Product>
    fun save(product: Product): Product
}

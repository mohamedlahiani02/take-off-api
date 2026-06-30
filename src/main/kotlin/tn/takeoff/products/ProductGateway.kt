package tn.takeoff.products

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.Optional
import java.util.UUID

interface ProductGateway {
    fun findById(id: UUID): Optional<Product>
    fun search(category: ProductCategory?, search: String?, pageable: Pageable): Page<Product>
}

package tn.takeoff.products

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tn.takeoff.common.errors.NotFoundException
import java.util.UUID

@Service
class ProductService(private val repo: ProductGateway) {

    @Transactional(readOnly = true)
    fun list(category: String?, search: String?, page: Int, size: Int): Page<Product> {
        val cat = category?.let { runCatching { ProductCategory.valueOf(it.uppercase()) }.getOrNull() }
        val q = search?.ifBlank { null }
        val pageable = PageRequest.of(page, size, Sort.by("name"))
        return when {
            cat != null && q != null -> repo.searchActiveByCategory(cat, q, pageable)
            cat != null              -> repo.findByIsActiveTrueAndCategory(cat, pageable)
            q != null                -> repo.searchActive(q, pageable)
            else                     -> repo.findByIsActiveTrue(pageable)
        }
    }

    @Transactional(readOnly = true)
    fun getById(id: UUID): Product =
        repo.findById(id).orElseThrow { NotFoundException("product", id) }
}

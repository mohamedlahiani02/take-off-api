package tn.takeoff.products

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import tn.takeoff.common.errors.NotFoundException
import java.util.UUID

@Service
class ProductService(private val repo: ProductGateway) {

    fun list(category: String?, search: String?, page: Int, size: Int): Page<Product> {
        val cat = category?.let { runCatching { ProductCategory.valueOf(it.uppercase()) }.getOrNull() }
        val pageable = PageRequest.of(page, size, Sort.by("name"))
        return repo.search(cat, search?.ifBlank { null }, pageable)
    }

    fun getById(id: UUID): Product =
        repo.findById(id).orElseThrow { NotFoundException("product", id) }
}

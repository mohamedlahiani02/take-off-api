package tn.takeoff.products

import org.springframework.data.domain.Page
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/products")
class ProductController(private val service: ProductService) {

    @GetMapping
    fun list(
        @RequestParam category: String? = null,
        @RequestParam search: String? = null,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Page<Product> = service.list(category, search, page, size)

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): Product = service.getById(id)
}

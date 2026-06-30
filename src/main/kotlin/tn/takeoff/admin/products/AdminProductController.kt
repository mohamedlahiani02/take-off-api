package tn.takeoff.admin.products

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import tn.takeoff.admin.audit.AuditService
import tn.takeoff.auth.JwtService
import tn.takeoff.common.errors.NotFoundException
import tn.takeoff.products.Product
import tn.takeoff.products.ProductCategory
import tn.takeoff.products.ProductRepository
import tn.takeoff.products.ProductVariant
import tn.takeoff.products.ProductVariantRepository
import java.math.BigDecimal
import java.util.UUID

data class VariantRequest(val size: String, val stock: Int = 0)

data class ProductRequest(
    @field:NotBlank val name: String,
    @field:NotNull val category: ProductCategory,
    @field:NotNull val priceDt: BigDecimal,
    val description: String? = null,
    val stock: Int = 0,
    val tag: String? = null,
    val imageUrls: List<String> = emptyList(),
    val isActive: Boolean = true,
    /** When non-empty, the product "has sizes" and these are its per-size variants. */
    val variants: List<VariantRequest> = emptyList(),
)

data class ProductDetail(val product: Product, val variants: List<ProductVariant>)

@Service
class AdminProductService(
    private val products: ProductRepository,
    private val variants: ProductVariantRepository,
    private val auditService: AuditService,
) {
    fun list(): List<ProductDetail> = products.findAll().map {
        ProductDetail(it, variants.findByProductIdOrderByDisplayOrder(it.id))
    }

    @Transactional
    fun create(req: ProductRequest, adminId: UUID): ProductDetail {
        val p = Product(
            name = req.name, category = req.category, priceDt = req.priceDt, description = req.description,
            hasSizes = req.variants.isNotEmpty(), stock = req.stock, tag = req.tag,
            imageUrls = req.imageUrls.toTypedArray(), isActive = req.isActive,
        )
        products.save(p)
        saveVariants(p.id, req.variants)
        auditService.log(adminId, "product.create", "product", p.id.toString())
        return ProductDetail(p, variants.findByProductIdOrderByDisplayOrder(p.id))
    }

    @Transactional
    fun update(id: UUID, req: ProductRequest, adminId: UUID): ProductDetail {
        val p = products.findById(id).orElseThrow { NotFoundException("product", id) }
        p.name = req.name; p.category = req.category; p.priceDt = req.priceDt; p.description = req.description
        p.hasSizes = req.variants.isNotEmpty(); p.stock = req.stock; p.tag = req.tag
        p.imageUrls = req.imageUrls.toTypedArray(); p.isActive = req.isActive
        products.save(p)
        variants.deleteByProductId(id)
        saveVariants(id, req.variants)
        auditService.log(adminId, "product.update", "product", id.toString())
        return ProductDetail(p, variants.findByProductIdOrderByDisplayOrder(id))
    }

    private fun saveVariants(productId: UUID, list: List<VariantRequest>) {
        list.forEachIndexed { i, v ->
            variants.save(ProductVariant(productId = productId, size = v.size.trim(), stock = v.stock, displayOrder = i))
        }
    }

    @Transactional
    fun delete(id: UUID, adminId: UUID) {
        val p = products.findById(id).orElseThrow { NotFoundException("product", id) }
        products.delete(p) // variants cascade via FK ON DELETE CASCADE
        auditService.log(adminId, "product.delete", "product", id.toString())
    }
}

@RestController
@RequestMapping("/api/v1/admin/products")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
class AdminProductController(private val service: AdminProductService) {

    @GetMapping fun list(): List<ProductDetail> = service.list()

    @PostMapping
    fun create(@Valid @RequestBody req: ProductRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): ProductDetail =
        service.create(req, a.adminId)

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody req: ProductRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): ProductDetail =
        service.update(id, req, a.adminId)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID, @AuthenticationPrincipal a: JwtService.AdminClaims) = service.delete(id, a.adminId)
}

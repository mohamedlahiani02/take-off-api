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
import java.math.BigDecimal
import java.util.UUID

data class ProductRequest(
    @field:NotBlank val name: String,
    @field:NotNull val category: ProductCategory,
    @field:NotNull val priceDt: BigDecimal,
    val description: String? = null,
    val hasSizes: Boolean = false,
    val stock: Int = 0,
    val tag: String? = null,
    val imageUrls: List<String> = emptyList(),
    val isActive: Boolean = true,
)

@Service
class AdminProductService(
    private val products: ProductRepository,
    private val auditService: AuditService,
) {
    fun list(): List<Product> = products.findAll()

    @Transactional
    fun create(req: ProductRequest, adminId: UUID): Product {
        val p = Product(
            name = req.name, category = req.category, priceDt = req.priceDt, description = req.description,
            hasSizes = req.hasSizes, stock = req.stock, tag = req.tag,
            imageUrls = req.imageUrls.toTypedArray(), isActive = req.isActive,
        )
        products.save(p)
        auditService.log(adminId, "product.create", "product", p.id.toString())
        return p
    }

    @Transactional
    fun update(id: UUID, req: ProductRequest, adminId: UUID): Product {
        val p = products.findById(id).orElseThrow { NotFoundException("product", id) }
        p.name = req.name; p.category = req.category; p.priceDt = req.priceDt; p.description = req.description
        p.hasSizes = req.hasSizes; p.stock = req.stock; p.tag = req.tag
        p.imageUrls = req.imageUrls.toTypedArray(); p.isActive = req.isActive
        products.save(p)
        auditService.log(adminId, "product.update", "product", id.toString())
        return p
    }

    @Transactional
    fun delete(id: UUID, adminId: UUID) {
        val p = products.findById(id).orElseThrow { NotFoundException("product", id) }
        products.delete(p)
        auditService.log(adminId, "product.delete", "product", id.toString())
    }
}

@RestController
@RequestMapping("/api/v1/admin/products")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
class AdminProductController(private val service: AdminProductService) {

    @GetMapping fun list(): List<Product> = service.list()

    @PostMapping
    fun create(@Valid @RequestBody req: ProductRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): Product =
        service.create(req, a.adminId)

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody req: ProductRequest, @AuthenticationPrincipal a: JwtService.AdminClaims): Product =
        service.update(id, req, a.adminId)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID, @AuthenticationPrincipal a: JwtService.AdminClaims) = service.delete(id, a.adminId)
}

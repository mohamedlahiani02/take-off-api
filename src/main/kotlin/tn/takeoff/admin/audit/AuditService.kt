package tn.takeoff.admin.audit

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuditService(private val repo: AuditGateway) {

    fun log(
        adminId: UUID,
        action: String,
        targetType: String? = null,
        targetId: String? = null,
        payload: Map<String, Any>? = null,
    ) {
        repo.save(AuditLog(
            adminId = adminId,
            action = action,
            targetType = targetType,
            targetId = targetId,
            payload = payload,
        ))
    }
}

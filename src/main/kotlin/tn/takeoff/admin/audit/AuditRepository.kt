package tn.takeoff.admin.audit

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AuditRepository : JpaRepository<AuditLog, UUID>, AuditGateway

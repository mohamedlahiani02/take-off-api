package tn.takeoff.admin.audit

interface AuditGateway {
    fun save(log: AuditLog): AuditLog
}

package tn.takeoff.coaching

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CoachingRepository : JpaRepository<CoachingInquiry, UUID>, CoachingGateway

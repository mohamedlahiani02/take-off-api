package tn.takeoff.courts

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface CourtBlockRepository : JpaRepository<CourtBlock, UUID> {

    fun findByStartsAtGreaterThanEqualAndStartsAtLessThan(from: Instant, to: Instant): List<CourtBlock>

    fun findByCourtId(courtId: UUID): List<CourtBlock>

    // One-off blocks that overlap a candidate slot on a court (recurring handled in service).
    fun findByCourtIdAndStartsAtLessThanAndEndsAtGreaterThan(
        courtId: UUID,
        endsAt: Instant,
        startsAt: Instant,
    ): List<CourtBlock>
}


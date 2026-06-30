package tn.takeoff.coaching

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface CoachingGateway {
    fun save(inquiry: CoachingInquiry): CoachingInquiry
    fun findAll(pageable: Pageable): Page<CoachingInquiry>
}

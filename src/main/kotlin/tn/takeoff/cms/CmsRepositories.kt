package tn.takeoff.cms

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface SiteContentRepository : JpaRepository<SiteContent, UUID> {
    fun findByPageOrderByDisplayOrder(page: String): List<SiteContent>
    fun findByPageAndSectionKey(page: String, sectionKey: String): Optional<SiteContent>
}

interface GlobalSettingRepository : JpaRepository<GlobalSetting, String>

interface MediaAssetRepository : JpaRepository<MediaAsset, UUID> {
    fun findAllByOrderByCreatedAtDesc(): List<MediaAsset>
}

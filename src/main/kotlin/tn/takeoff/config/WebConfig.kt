package tn.takeoff.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.io.File

@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val dir = System.getenv("COACH_PHOTOS_DIR") ?: "uploads/coaches"
        val location = "file:${File(dir).absolutePath}/"
        registry.addResourceHandler("/uploads/coaches/**")
            .addResourceLocations(location)
    }
}

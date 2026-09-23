package tn.takeoff.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tn.takeoff.admin.auth.AdminJwtAuthFilter
import tn.takeoff.auth.JwtAuthFilter
import tn.takeoff.auth.JwtService
import tn.takeoff.users.UserGateway

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtService: JwtService,
    private val env: Environment,
    private val userGateway: UserGateway,
) {

    @Bean
    fun adminJwtAuthFilter(): AdminJwtAuthFilter = AdminJwtAuthFilter(jwtService)

    @Bean
    fun memberJwtAuthFilter(): JwtAuthFilter = JwtAuthFilter(jwtService, userGateway)

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            val originsStr = env.getProperty("CORS_ALLOWED_ORIGINS", "http://localhost:3000,http://localhost:5500")
            val origins = originsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            allowedOriginPatterns = origins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600L
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }

    @Bean
    @Order(1)
    fun adminFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/api/v1/admin/**")
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/login").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .addFilterBefore(adminJwtAuthFilter(), UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    @Order(2)
    fun memberFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/send-otp",
                        "/api/v1/auth/verify-otp",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password",
                    ).permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/products/**", "/api/v1/courts/**", "/api/v1/coaches/**", "/api/v1/content/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/tournaments", "/api/v1/tournaments/*").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/classes/schedule", "/api/v1/classes/packs").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/coaching/inquiry").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                    .requestMatchers(
                        "/error",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/actuator/health",
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .addFilterBefore(memberJwtAuthFilter(), UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)
}


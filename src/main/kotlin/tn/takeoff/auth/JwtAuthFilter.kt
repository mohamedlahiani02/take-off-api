package tn.takeoff.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import tn.takeoff.users.AccountStatus
import tn.takeoff.users.UserGateway

class JwtAuthFilter(
    private val jwtService: JwtService,
    private val userGateway: UserGateway,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.removePrefix("Bearer ")
            runCatching {
                val claims = jwtService.verify(token)
                val user = userGateway.findById(claims.userId).orElse(null)
                if (user != null && user.accountStatus == AccountStatus.ACTIVE) {
                    val auth = UsernamePasswordAuthenticationToken(
                        claims,
                        null,
                        listOf(SimpleGrantedAuthority("ROLE_${claims.role.name}")),
                    )
                    SecurityContextHolder.getContext().authentication = auth
                }
            }
        }
        chain.doFilter(request, response)
    }
}

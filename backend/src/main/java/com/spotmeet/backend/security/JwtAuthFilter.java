package com.spotmeet.backend.security;

import com.spotmeet.backend.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Loads the account and validates JWT expiration and revocation for each request.
 * Missing or invalid tokens leave the request unauthenticated.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // No header or wrong format: continue without authentication
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            final String email = jwtUtil.extractEmail(jwt);

            // Only proceed if there is an e-mail and the request is not authenticated yet
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (userDetails instanceof User user
                        && jwtUtil.isTokenValid(jwt, user.getUsername(), user.getTokensValidAfter())) {
                    // Users with a pending e-mail verification are not authenticated
                    if (!userDetails.isEnabled()) {
                        logger.warn("JWT rejected for an unverified account.");
                    } else {
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            }
        } catch (DataAccessException e) {
            logger.warn("JWT account lookup unavailable.");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        } catch (Exception e) {
            // Corrupted or expired token, or user not found. No exception is thrown:
            // the chain continues and the SecurityFilterChain returns 401 for protected routes.
            logger.warn("JWT rejected.");
        }

        filterChain.doFilter(request, response);
    }
}

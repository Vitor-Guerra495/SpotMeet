package com.spotmeet.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
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
 * JWT filter, executed once per request.
 *
 * Flow:
 * 1. Reads the Authorization header (Bearer token).
 * 2. Extracts and validates the JWT through JwtUtil.
 * 3. If valid, populates the SecurityContextHolder with the user authentication.
 * 4. If the token is missing or invalid, the request continues unauthenticated
 *    (the SecurityFilterChain returns 401 for protected routes).
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

                if (jwtUtil.isTokenValid(jwt, userDetails.getUsername())) {
                    // Users with a pending e-mail verification are not authenticated
                    if (!userDetails.isEnabled()) {
                        logger.warn("[SpotMeet-JWT] Acesso restrito: e-mail pendente de validação para: " + email);
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
        } catch (Exception e) {
            // Corrupted or expired token, or user not found. No exception is thrown:
            // the chain continues and the SecurityFilterChain returns 401 for protected routes.
            logger.warn("[SpotMeet-JWT] Token rejeitado: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}

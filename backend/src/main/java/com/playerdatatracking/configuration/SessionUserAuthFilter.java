package com.playerdatatracking.configuration;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionUserAuthFilter extends OncePerRequestFilter {

    @Override
    @SuppressWarnings("unchecked")
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, java.io.IOException {
        var session = req.getSession(false);
        if (session != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Object user = session.getAttribute("USER");
            if (user instanceof Map<?, ?> u) {
                String username = String.valueOf(u.get("username"));

                List<String> roles = (List<String>) u.get("roles");
                List<SimpleGrantedAuthority> authorities = (roles != null && !roles.isEmpty())
                        ? roles.stream()
                                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                                .collect(Collectors.toList())
                        : List.of(new SimpleGrantedAuthority("ROLE_USER"));

                var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(req, res);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String p = req.getRequestURI();
        return p.matches("^/players/\\d+/photo$");
    }
}

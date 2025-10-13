package com.playerdatatracking.configuration;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionUserAuthFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, java.io.IOException {
    var session = req.getSession(false);
    if (session != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      Object user = session.getAttribute("USER");
      if (user instanceof Map<?,?> u) {
        String username = String.valueOf(u.get("username"));
        var auth = new UsernamePasswordAuthenticationToken(
            username, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
      }
    }
    chain.doFilter(req, res);
  }
}


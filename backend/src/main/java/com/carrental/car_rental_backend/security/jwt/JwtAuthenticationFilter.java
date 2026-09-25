package com.carrental.car_rental_backend.security.jwt;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.carrental.car_rental_backend.auth.service.CustomUserDetailsService;
import com.carrental.car_rental_backend.security.context.TenantContext;
import com.carrental.car_rental_backend.security.principal.UserPrincipal;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter{
  private final JwtProvider jwtProvider;
  private final CustomUserDetailsService customUserDetailsService;

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    try {
      String token = parseBearerToken(request);
      
      if (StringUtils.hasText(token) && jwtProvider.validateToken(token)){
        Claims claims = jwtProvider.parseClaims(token);

        String userIdStr = claims.getSubject();
        String role = claims.get("role", String.class);
        String tenantIdStr = claims.get("tenant_id", String.class);
        String activeBranchIdStr = claims.get("active_branch_id", String.class);

        UUID tenantUUID = null;
        UUID activeBranchUUID = null;
        UUID userUUID = null;

        if (tenantIdStr != null) {
          tenantUUID = UUID.fromString(tenantIdStr);
          TenantContext.setTenantId(tenantUUID);
        }
        if (activeBranchIdStr != null) {
          activeBranchUUID = UUID.fromString(activeBranchIdStr);
          TenantContext.setBranchId(activeBranchUUID);
        }
        if(userIdStr != null) {
          userUUID = UUID.fromString(userIdStr);
        }
        if (role != null) {
          TenantContext.setRole(role);
        }

        UsernamePasswordAuthenticationToken authentication = null;
        UserPrincipal userPrincipal = null;

        if(StringUtils.hasText(userIdStr) && StringUtils.hasText(tenantIdStr)){
          userPrincipal = this.customUserDetailsService.loadUserByIdAndTenantId(userUUID, tenantUUID);
        }else{
          userPrincipal = this.customUserDetailsService.loadSuperAdminById(userUUID);
        }
        authentication = new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  private String parseBearerToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
      return header.substring(7);
    }
    return null;
  }
}

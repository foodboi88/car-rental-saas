package com.carrental.car_rental_backend.security.jwt;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.carrental.car_rental_backend.account.entity.User;
import com.carrental.car_rental_backend.account.repository.UserRepository;
import com.carrental.car_rental_backend.auth.service.CustomUserDetailsService;
import com.carrental.car_rental_backend.common.constant.ClaimKey;
import com.carrental.car_rental_backend.common.constant.RoleConstant;
import com.carrental.car_rental_backend.common.constant.TokenType;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
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
  private final HandlerExceptionResolver handlerExceptionResolver;
  private final UserRepository userRepository;

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    try {
      try{
        String token = parseBearerToken(request);
      
        if (StringUtils.hasText(token) && this.jwtProvider.validateToken(token)){
          Claims claims = this.jwtProvider.parseClaims(token);

          String userIdStr = claims.getSubject();
          if (!StringUtils.hasText(userIdStr)) throw new AppException(ErrorCode.UNAUTHORIZED);

          String tokenType = claims.get(TokenType.CLAIM_TOKEN_TYPE, String.class);
          if (!TokenType.TOKEN_TYPE_ACCESS.equals(tokenType)) throw new AppException(ErrorCode.UNAUTHORIZED);

          String currentSessionId = claims.get(ClaimKey.CURRENT_SESSION_ID, String.class);
          if (!StringUtils.hasText(currentSessionId)) throw new AppException(ErrorCode.BAD_REQUEST);
          
          Optional<User> user = this.userRepository.findById(UUID.fromString(userIdStr));
          if (user.isEmpty()) throw new AppException(ErrorCode.UNAUTHORIZED);
          if (user.get().getCurrentSessionId() == null) throw new AppException(ErrorCode.UNAUTHORIZED, "Tài khoản này đã đăng xuất, vui lòng đăng nhập lại !");
          if (!UUID.fromString(currentSessionId).equals(user.get().getCurrentSessionId())) throw new AppException(ErrorCode.FORBIDDEN, "Tài khoản này hiện đang có người sử dụng. Vui lòng đăng nhập lại !");

          String tenantIdStr = claims.get(ClaimKey.TENANT_ID, String.class);
          String activeBranchIdStr = claims.get(ClaimKey.ACTIVE_BRANCH_ID, String.class);
          String role = claims.get(ClaimKey.ROLE, String.class);

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
          if (userIdStr != null) {
            userUUID = UUID.fromString(userIdStr);
            TenantContext.setUserId(userUUID);
          }
          if (role != null) {
            TenantContext.setRole(role);
          }
          if (StringUtils.hasText(currentSessionId)) {
            TenantContext.setCurrentSessionId(UUID.fromString(currentSessionId));
          }

          UsernamePasswordAuthenticationToken authentication = null;
          UserPrincipal userPrincipal = null;

          if (StringUtils.hasText(userIdStr) && StringUtils.hasText(tenantIdStr)) {
            userPrincipal = this.customUserDetailsService.loadUserByIdAndTenantId(userUUID, tenantUUID);
          } else if (RoleConstant.SUPER_ADMIN.equals(role)){
            userPrincipal = this.customUserDetailsService.loadSuperAdminById(userUUID);
          } else {
            userPrincipal = this.customUserDetailsService.loadUserById(userUUID);
          }
          authentication = new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
          authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(authentication);
        }
      } catch (Exception exception) {
        this.handlerExceptionResolver.resolveException(request, response, null, exception);
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        return;
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

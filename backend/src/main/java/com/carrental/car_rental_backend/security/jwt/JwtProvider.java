package com.carrental.car_rental_backend.security.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtProvider {

  private final SecretKey key;
  private final long accessTokenExpirationMs;
  private final long refreshTokenExpirationMs;

  public JwtProvider(
    @Value("${jwt.secret}") String secret,
    @Value("${jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
    @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs
  ){
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenExpirationMs = accessTokenExpirationMs;
    this.refreshTokenExpirationMs = refreshTokenExpirationMs;
  }

  public String generateAccessToken(UUID userId, String email, String role, UUID tenantId, UUID activeBranchId) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);
    
    var builder = Jwts.builder()
      .subject(userId.toString())
      .claim("email", email)
      .claim("role", role)
      .issuedAt(now)
      .expiration(expiryDate)
      .signWith(key);
      
    if (tenantId != null) {
      builder.claim("tenant_id", tenantId.toString());
    }

    if(activeBranchId != null) {
      builder.claim("active_branch_id", activeBranchId.toString());
    }

    return builder.compact();
  }

  public String generateRefreshToken(UUID userId) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + this.refreshTokenExpirationMs);

    return Jwts.builder()
      .subject(userId.toString())
      .issuedAt(now)
      .expiration(expiryDate)
      .signWith(key)
      .compact();
  }

  public UUID getUserIdFromToken(String token) {
    if(!this.validateToken(token)) throw new AppException(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getMessage());
    return UUID.fromString(this.parseClaims(token).getSubject());
  }

  public Claims parseClaims(String token) {
    return Jwts.parser()
      .verifyWith(key)
      .build()
      .parseSignedClaims(token)
      .getPayload();
  }

  public boolean validateToken(String token) {
    try{
      Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
      return true;
    }catch (ExpiredJwtException ex) {
      log.error("JWT Token đã hết hạn: {}", ex.getMessage());
    }catch (SignatureException ex) {
      log.error("JWT Token chữ ký giả mạo / không đúng secret key: {}", ex.getMessage());
    }catch (MalformedJwtException ex) {
      log.error("JWT Token sai định dạng: {}", ex.getMessage());
    }catch (IllegalArgumentException ex) {
      log.error("JWT Token bị rỗng/null: {}", ex.getMessage());
    }catch (Exception ex) {
      log.error("JWT Token không hợp lệ: {}", ex.getMessage());
    }
    return false;
  }
  
}

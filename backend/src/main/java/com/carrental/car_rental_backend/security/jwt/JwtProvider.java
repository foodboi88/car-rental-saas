package com.carrental.car_rental_backend.security.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.carrental.car_rental_backend.common.constant.ClaimKey;
import com.carrental.car_rental_backend.common.constant.TokenType;
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

  public String generateAccessToken(UUID userId, String email, String roleCode, UUID tenantId, UUID activeBranchId, UUID currentSessionId) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);
    
    var builder = Jwts.builder()
      .subject(userId.toString())
      .claim(TokenType.CLAIM_TOKEN_TYPE, TokenType.TOKEN_TYPE_ACCESS)
      .claim(ClaimKey.EMAIL, email)
      .claim(ClaimKey.ROLE, roleCode)
      .claim(ClaimKey.CURRENT_SESSION_ID, currentSessionId)
      .issuedAt(now)
      .expiration(expiryDate)
      .signWith(key);
      
    if (tenantId != null) {
      builder.claim(ClaimKey.TENANT_ID, tenantId.toString());
    }

    if(activeBranchId != null) {
      builder.claim(ClaimKey.ACTIVE_BRANCH_ID, activeBranchId.toString());
    }

    return builder.compact();
  }

  public String generateRefreshToken(UUID userId, UUID currentSessionId) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + this.refreshTokenExpirationMs);

    return Jwts.builder()
      .subject(userId.toString())
      .claim(TokenType.CLAIM_TOKEN_TYPE, TokenType.TOKEN_TYPE_REFRESH)
      .claim(ClaimKey.CURRENT_SESSION_ID, currentSessionId)
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
      throw new AppException(ErrorCode.UNAUTHORIZED, "JWT Token đã hết hạn");
    }catch (SignatureException ex) {
      log.error("JWT Token chữ ký giả mạo / không đúng secret key: {}", ex.getMessage());
      throw new AppException(ErrorCode.UNAUTHORIZED, "JWT Token chữ ký giả mạo / không đúng secret key");
    }catch (MalformedJwtException ex) {
      log.error("JWT Token sai định dạng: {}", ex.getMessage());
      throw new AppException(ErrorCode.UNAUTHORIZED, "JWT Token sai định dạng");
    }catch (IllegalArgumentException ex) {
      log.error("JWT Token bị rỗng/null: {}", ex.getMessage());
      throw new AppException(ErrorCode.UNAUTHORIZED, "JWT Token bị rỗng/null");
    }catch (Exception ex) {
      log.error("JWT Token không hợp lệ: {}", ex.getMessage());
      throw new AppException(ErrorCode.UNAUTHORIZED, "JWT Token không hợp lệ");
    }
  }

  public long getAccessTokenExpirationInSeconds() {
    return this.accessTokenExpirationMs / 1000;
  }
  
}

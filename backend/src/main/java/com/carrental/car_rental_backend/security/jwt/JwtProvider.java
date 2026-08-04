package com.carrental.car_rental_backend.security.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtProvider {

  private final SecretKey key;
  private final long accessTokenExpirationMs;

  public JwtProvider(
    @Value("${jwt.secret}") String secret,
    @Value("${jwt.access-token-expiration-ms}") long accessTokenExpirationMs
  ){
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenExpirationMs = accessTokenExpirationMs;
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
    } catch (Exception ex) {
      log.error("Invalid JWT Token: {}", ex.getMessage());
    }
    return false;
  }
  
}

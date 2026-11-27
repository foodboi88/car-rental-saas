package com.carrental.car_rental_backend.auth.entity;

import java.time.Instant;
import java.util.UUID;

import org.springframework.util.StringUtils;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_sessions")
public class UserSession {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Builder.Default
  @Column(name = "device_name", nullable = false, length = 255)
  private String deviceName = "Unknown Device";

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Builder.Default
  @Column(name = "is_revoked", nullable = false)
  private Boolean isRevoked = false;

  @Column(name = "last_active_at", nullable = false)
  private Instant lastActiveAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    if (this.createdAt == null) this.createdAt = Instant.now();
    if (this.lastActiveAt == null) this.lastActiveAt = Instant.now();
    if (!StringUtils.hasText(this.deviceName)) this.deviceName = "Unknown Device";
    if (this.isRevoked == null) this.isRevoked = false;
  }
}

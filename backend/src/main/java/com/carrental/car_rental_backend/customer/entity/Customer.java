package com.carrental.car_rental_backend.customer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "customers")
public class Customer {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "phone", length = 20)
  private String phone;

  @Column(name = "email", length = 255)
  private String email;

  @Column(name = "address", columnDefinition = "TEXT")
  private String address;

  @Column(name = "risk_level", nullable = false)
  private Integer riskLevel; // 1:SAFE, 2:WARNING, 3:BLACKLIST

  @Column(name = "blacklist_reason", columnDefinition = "TEXT")
  private String blacklistReason;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;

    if (riskLevel == null) {
      riskLevel = 1;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}

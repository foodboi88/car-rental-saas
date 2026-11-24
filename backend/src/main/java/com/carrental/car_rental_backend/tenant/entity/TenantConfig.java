package com.carrental.car_rental_backend.tenant.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(name = "tenant_configs")
public class TenantConfig {
  @Id
  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(name = "hold_timeout_minutes")
  private Integer holdTimeoutMinutes;

  @Column(name = "late_hourly_price", precision = 12, scale = 2)
  private BigDecimal lateHourlyPrice;

  @Column(name = "default_commission_rate", precision = 5, scale = 2)
  private BigDecimal defaultCommissionRate;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;
}

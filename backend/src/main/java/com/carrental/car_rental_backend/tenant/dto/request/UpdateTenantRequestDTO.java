package com.carrental.car_rental_backend.tenant.dto.request;

import com.carrental.car_rental_backend.common.enums.plan_tier.PlanTierTenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTenantRequestDTO {
  @NotBlank(message = "Vui lòng nhập tên nhà xe")
  @Size(max = 255, message = "Tên nhà xe tối đa 255 ký tự")
  private String name;

  @NotBlank(message = "Vui lòng nhập domain")
  @Size(max = 255, message = "Domain tối đa 255 ký tự")
  @Pattern(
    regexp = "^[a-z0-9]+([.-][a-z0-9]+)*$",
    message = "Domain chỉ gồm chữ thường, số, dấu chấm và gạch ngang"
  )
  private String domain;

  @NotNull(message = "Vui lòng chọn gói dịch vụ")
  private PlanTierTenant planTier;

  @Size(max = 500, message = "Logo URL tối đa 500 ký tự")
  private String logoUrl;

  @Email(message = "Email liên hệ không đúng định dạng")
  private String contactEmail;

  @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
  private String contactPhone;

  private String settings;
}

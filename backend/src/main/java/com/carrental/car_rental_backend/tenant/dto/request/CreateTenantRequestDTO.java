package com.carrental.car_rental_backend.tenant.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTenantRequestDTO {
  @NotBlank(message = "Vui lòng nhập tên nhà xe")
  @Size(max = 255, message = "Tên nhà xe không được vượt quá {max} ký tự")
  private String name;

  @NotBlank(message = "Vui lòng nhập domain của nhà xe")
  @Size(max = 255, message = "Domain của nhà xe không được vượt quá {max} ký tự")
  private String domain;

  @NotEmpty(message = "Vui lòng chọn ít nhất một chủ nhà xe")
  private List<@NotNull(message = "Mã tài khoản chủ nhà xe không được để trống") UUID> adminUserIds;

  @Size(max = 500, message = "Đường dẫn logo không được vượt quá {max} ký tự")
  private String logoUrl;

  @Email(message = "Email liên hệ không đúng định dạng")
  @Size(max = 255, message = "Email liên hệ không được vượt quá {max} ký tự")
  private String contactEmail;

  @Size(max = 20, message = "Số điện thoại liên hệ không được vượt quá {max} ký tự")
  private String contactPhone;
}

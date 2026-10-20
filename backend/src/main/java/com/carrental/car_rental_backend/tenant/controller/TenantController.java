package com.carrental.car_rental_backend.tenant.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.carrental.car_rental_backend.common.dto.ApiResponse;
import com.carrental.car_rental_backend.security.principal.UserPrincipal;
import com.carrental.car_rental_backend.tenant.dto.request.AdminAssignmentRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.request.CreateTenantRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.request.UpdateTenantRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.request.UpdateTenantStatusRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.response.CreateTenantResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.TenantAdminResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.TenantResponseDTO;
import com.carrental.car_rental_backend.tenant.service.TenantService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')") // Ở cấp lớp: mọi endpoint bên dưới chỉ Super Admin được gọi
@RequiredArgsConstructor // Sinh ra constructor nhận các field final của class (Ở đây là: TenantService)
public class TenantController {
  private static final int MAX_PAGE_SIZE = 100;

  private final TenantService tenantService;

  @PostMapping
  public ResponseEntity<ApiResponse<CreateTenantResponseDTO>> createTenant(
    // Lấy thông tin user đang đăng nhập từ hệ thống xác thực
    @AuthenticationPrincipal UserPrincipal userPrincipal,
    // @Valid kiểm tra các điều kiện trên DTO
    // @RequestBody: đọc JSON -> Object
    @Valid @RequestBody CreateTenantRequestDTO request
  ) {
    CreateTenantResponseDTO response = this.tenantService.createTenant(request, userPrincipal.getId());

    return ResponseEntity.status(HttpStatus.CREATED).body(
      ApiResponse.success(response, "Tạo nhà xe thành công !")
    );
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PagedModel<TenantResponseDTO>>> listTenants(
    @RequestParam(required = false) String keyword,
    @RequestParam(required = false) Boolean isActive,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "10") int size
  ) {
    PageRequest pageRequest = PageRequest.of(
      Math.max(page, 0),
      Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
      Sort.by(Sort.Direction.DESC, "createdAt")
    );
    Page<TenantResponseDTO> response = this.tenantService.listTenants(keyword, isActive, pageRequest);

    return ResponseEntity.ok(
      ApiResponse.success(new PagedModel<>(response), "Lấy danh sách nhà xe thành công !")
    );
  }

  @GetMapping("/{tenantId}")
  public ResponseEntity<ApiResponse<TenantResponseDTO>> getTenant(@PathVariable UUID tenantId) {
    TenantResponseDTO response = this.tenantService.getTenantById(tenantId);

    return ResponseEntity.ok(
      ApiResponse.success(response, "Lấy thông tin nhà xe thành công !")
    );
  }

  @PutMapping("/{tenantId}")
  public ResponseEntity<ApiResponse<TenantResponseDTO>> updateTenant(
    @AuthenticationPrincipal UserPrincipal userPrincipal,
    @PathVariable UUID tenantId,
    @Valid @RequestBody UpdateTenantRequestDTO request
  ) {
    TenantResponseDTO response = this.tenantService.updateTenant(tenantId, request, userPrincipal.getId());

    return ResponseEntity.ok(
      ApiResponse.success(response, "Cập nhật nhà xe thành công !")
    );
  }

  @PatchMapping("/{tenantId}/status")
  public ResponseEntity<ApiResponse<TenantResponseDTO>> changeStatus(
    @AuthenticationPrincipal UserPrincipal userPrincipal,
    @PathVariable UUID tenantId,
    @Valid @RequestBody UpdateTenantStatusRequestDTO request
  ) {
    TenantResponseDTO response = this.tenantService.changeStatus(tenantId, request.getIsActive(), userPrincipal.getId());

    return ResponseEntity.ok(
      ApiResponse.success(response, Boolean.TRUE.equals(request.getIsActive()) ? "Mở khóa nhà xe thành công !" : "Khóa nhà xe thành công !")
    );
  }

  @DeleteMapping("/{tenantId}")
  public ResponseEntity<ApiResponse<Void>> deleteTenant(
    @AuthenticationPrincipal UserPrincipal userPrincipal,
    @PathVariable UUID tenantId
  ) {
    this.tenantService.deleteTenant(tenantId, userPrincipal.getId());

    return ResponseEntity.ok(
      ApiResponse.success(null, "Đã khóa nhà xe (xóa mềm) thành công !")
    );
  }

  @GetMapping("/{tenantId}/admins")
  public ResponseEntity<ApiResponse<List<TenantAdminResponseDTO>>> listTenantAdmins(@PathVariable UUID tenantId) {
    List<TenantAdminResponseDTO> response = this.tenantService.listTenantAdmins(tenantId);

    return ResponseEntity.ok(
      ApiResponse.success(response, "Lấy danh sách chủ nhà xe thành công !")
    );
  }

  @PostMapping("/{tenantId}/admins")
  public ResponseEntity<ApiResponse<CreateTenantResponseDTO.AdminResult>> addTenantAdmin(
    @AuthenticationPrincipal UserPrincipal userPrincipal,
    @PathVariable UUID tenantId,
    @Valid @RequestBody AdminAssignmentRequestDTO request
  ) {
    CreateTenantResponseDTO.AdminResult response = this.tenantService.addTenantAdmin(tenantId, request, userPrincipal.getId());

    return ResponseEntity.status(HttpStatus.CREATED).body(
      ApiResponse.success(response, "Thêm chủ nhà xe thành công !")
    );
  }
}

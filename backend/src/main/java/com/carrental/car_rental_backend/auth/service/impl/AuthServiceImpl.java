package com.carrental.car_rental_backend.auth.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.carrental.car_rental_backend.account.entity.Role;
import com.carrental.car_rental_backend.account.entity.User;
import com.carrental.car_rental_backend.account.entity.UserTenant;
import com.carrental.car_rental_backend.account.repository.PermissionRepository;
import com.carrental.car_rental_backend.account.repository.RoleRepository;
import com.carrental.car_rental_backend.account.repository.UserRepository;
import com.carrental.car_rental_backend.account.repository.UserTenantRepository;
import com.carrental.car_rental_backend.auth.dto.request.LoginRequestDTO;
import com.carrental.car_rental_backend.auth.dto.request.RefreshTokenRequestDTO;
import com.carrental.car_rental_backend.auth.dto.request.SelectTenantRequestDTO;
import com.carrental.car_rental_backend.auth.dto.response.AuthResponseDTO;
import com.carrental.car_rental_backend.auth.dto.response.BranchSummaryDTO;
import com.carrental.car_rental_backend.auth.dto.response.TenantSummaryDTO;
import com.carrental.car_rental_backend.auth.dto.response.UserInfoResponseDTO;
import com.carrental.car_rental_backend.auth.service.AuthService;
import com.carrental.car_rental_backend.branch.entity.Branch;
import com.carrental.car_rental_backend.branch.repository.BranchRepository;
import com.carrental.car_rental_backend.common.constant.RoleConstant;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.security.jwt.JwtProvider;
import com.carrental.car_rental_backend.tenant.entity.Tenant;
import com.carrental.car_rental_backend.tenant.repository.TenantRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService{
  private final UserRepository userRepository;
  private final UserTenantRepository userTenantRepository;
  private final RoleRepository roleRepository;
  private final TenantRepository tenantRepository;
  private final JwtProvider jwtProvider;
  private final PasswordEncoder passwordEncoder;
  private final BranchRepository branchRepository;
  private final PermissionRepository permissionRepository;

  @Override
  @Transactional
  public AuthResponseDTO login(LoginRequestDTO request) {
    Optional<User> user = this.userRepository.findByEmail(request.getEmail());
    if(user.isEmpty() || !this.passwordEncoder.matches(request.getPassword(), user.get().getPasswordHash())) throw new AppException(ErrorCode.UNAUTHORIZED, "Sai tài khoản hoặc mật khẩu !");
    User userInfo = user.get();
    if(!userInfo.getIsActive()) throw new AppException(ErrorCode.UNAUTHORIZED, "Tài khoản của bạn đã bị khóa !");

    if(Boolean.TRUE.equals(userInfo.getIsSuperAdmin())){
      String accessToken = this.jwtProvider.generateAccessToken(userInfo.getId(), userInfo.getEmail(), RoleConstant.SUPER_ADMIN, null, null);
      String refreshToken = this.jwtProvider.generateRefreshToken(userInfo.getId());
      return AuthResponseDTO.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .availableTenants(null)
        .expiresIn(this.jwtProvider.getAccessTokenExpirationInSeconds())
        .user(
          UserInfoResponseDTO.builder()
            .assignedBranches(null)
            .email(userInfo.getEmail())
            .fullname(userInfo.getFullname())
            .id(userInfo.getId())
            .permissions(null)
            .phone(userInfo.getPhone())
            .roleCode(RoleConstant.SUPER_ADMIN)
            .tenantId(null)
            .tenantName(null)
            .build()
        )
        .build();
    }

    List<UserTenant> listUserTenant = this.userTenantRepository.findByUserId(userInfo.getId());

    if(listUserTenant.size() == 1){ // Trường hợp user chỉ được phân công 1 nhà xe -> Trả list branch -> Vào màn chọn branch luôn
      UserTenant userTenant = listUserTenant.get(0);
      Optional<Role> role = this.roleRepository.findById(userTenant.getRoleId());
      if (role.isEmpty()) throw new AppException(ErrorCode.UNAUTHORIZED, "Tài khoản của bạn chưa được phân công vào Nhà xe nào !");
      String accessToken = this.jwtProvider.generateAccessToken(userInfo.getId(), userInfo.getEmail(), role.get().getCode(), userTenant.getTenantId(), null);
      String refreshToken = this.jwtProvider.generateRefreshToken(userInfo.getId());
      Optional<Tenant> tenant = this.tenantRepository.findById(userTenant.getTenantId());
      if (tenant.isEmpty()) throw new AppException(ErrorCode.UNAUTHORIZED, "Nhà xe của User không tồn tại !");
      Tenant tenantInfo = tenant.get();
      if (!tenantInfo.isActive()) throw new AppException(ErrorCode.FORBIDDEN, "Nhà xe của User hiện đang tạm ngưng hoạt động hoặc bị khóa");
      TenantSummaryDTO tenantSummaryDTO = TenantSummaryDTO.builder()
        .domain(tenantInfo.getDomain())
        .isActive(tenantInfo.isActive())
        .role(role.get().getCode()) // Role của user này ở tenant này
        .tenantId(tenantInfo.getId())
        .tenantName(tenantInfo.getName())
        .build();
      List<TenantSummaryDTO> listTenant = List.of(tenantSummaryDTO);
      List<Branch> listBranch = this.branchRepository.findAllAssignedBranchesByUserIdTenantId(userInfo.getId(), tenantInfo.getId());
      List<BranchSummaryDTO> listBranchSummaryDTO = listBranch.stream().map(
        item -> BranchSummaryDTO.builder()
        .branchId(item.getId())
        .branchName(item.getName())
        .build()
      ).toList();
      List<String> listPermission = this.permissionRepository.findAllPermissionCodeByRoleId(role.get().getId());

      return AuthResponseDTO.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .availableTenants(listTenant)
        .expiresIn(this.jwtProvider.getAccessTokenExpirationInSeconds())
        .user(
          UserInfoResponseDTO.builder()
            .assignedBranches(listBranchSummaryDTO)
            .email(userInfo.getEmail())
            .fullname(userInfo.getFullname())
            .id(userInfo.getId())
            .permissions(listPermission)
            .phone(userInfo.getPhone())
            .roleCode(role.get().getCode())
            .tenantId(userTenant.getTenantId())
            .tenantName(tenant.get().getName())
            .build()
        )
        .build();

    }else if(listUserTenant.size() > 1){ // Trường hợp user thuọc nhiều nhà xe -> Trả list tenant cho user chọn
      List<Tenant> listTenant = this.tenantRepository.findAllAssignedTenantsByUserId(userInfo.getId());
      String accessToken = this.jwtProvider.generateAccessToken(userInfo.getId(), userInfo.getEmail(), null, null, null);
      String refreshToken = this.jwtProvider.generateRefreshToken(userInfo.getId());
      List<UUID> listRoleIdOfUser = listUserTenant.stream().map(item -> item.getRoleId()).toList();
      List<Role> listRole = this.roleRepository.findAllById(listRoleIdOfUser);
      Map<UUID, String> roleIdToRoleCodeMapper = new HashMap<>(); // Mapper từ id ra code của role
      listRole.stream().forEach(item -> {
        roleIdToRoleCodeMapper.put(item.getId(), item.getCode());
      }); 
      Map<UUID, String> tenantIdToRoleCodeMapper = new HashMap<>(); // Mapper từ tenant id ra role của user trong tenant đó
      listUserTenant.stream().forEach(item -> {
        tenantIdToRoleCodeMapper.put(item.getTenantId(), roleIdToRoleCodeMapper.get(item.getRoleId()));
      });
      List<TenantSummaryDTO> tenantSummaryDTOList = listTenant.stream()
        .filter(item -> item.isActive() == true) // Chỉ lấy các nhà xe đang hoạt động
        .map(item -> TenantSummaryDTO.builder()
          .domain(item.getDomain())
          .isActive(item.isActive())
          .role(tenantIdToRoleCodeMapper.get(item.getId())) // Xử lý qua 2 mapper để tránh phải query trong for loop
          .tenantId(item.getId())
          .tenantName(item.getName())
          .build()
        ).toList();
      return AuthResponseDTO.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .availableTenants(tenantSummaryDTOList) // Danh sách tenant cho user chọn
        .expiresIn(this.jwtProvider.getAccessTokenExpirationInSeconds())
        .user(
          UserInfoResponseDTO.builder()
            .assignedBranches(null) // Danh sách branch thì phải chọn tenant đã mới có
            .email(userInfo.getEmail())
            .fullname(userInfo.getFullname())
            .id(userInfo.getId())
            .permissions(null)
            .phone(userInfo.getPhone())
            .roleCode(null)
            .tenantId(null)
            .tenantName(null)
            .build()
        )
        .build();
    }else{
      throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn chưa được phân công vào Tenant nào !");
    }
  }

  @Override
  public AuthResponseDTO selectTenant(UUID currentUserId, SelectTenantRequestDTO request) {
    

    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'selectTenant'");
  }

  @Override
  public AuthResponseDTO refreshToken(RefreshTokenRequestDTO request) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'refreshToken'");
  }

  @Override
  public UserInfoResponseDTO getMe() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'getMe'");
  }
  
}

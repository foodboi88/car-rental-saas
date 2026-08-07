package com.carrental.car_rental_backend.vehicle.service;

import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.vehicle.dto.CreateVehicleTypeRequestDTO;
import com.carrental.car_rental_backend.vehicle.dto.VehicleTypeResponseDTO;
import com.carrental.car_rental_backend.vehicle.entity.VehicleType;
import com.carrental.car_rental_backend.vehicle.repository.VehicleTypeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VehicleTypeService {
  private final VehicleTypeRepository vehicleTypeRepository;

  public VehicleTypeService(VehicleTypeRepository vehicleTypeRepository) {
    this.vehicleTypeRepository = vehicleTypeRepository;
  }

  public VehicleTypeResponseDTO createVehicleType(UUID tenantId, CreateVehicleTypeRequestDTO request) {
    String trimmedName = request.getName().trim();

    if (vehicleTypeRepository.existsByTenantIdAndNameIgnoreCase(tenantId, trimmedName)) {
      throw new AppException(ErrorCode.VEHICLE_TYPE_NAME_EXISTS);
    }

    VehicleType vehicleType = VehicleType.builder()
        .tenantId(tenantId)
        .name(trimmedName)
        .description(request.getDescription())
        .isActive(true)
        .build();

    VehicleType savedVehicleType = vehicleTypeRepository.save(vehicleType);
    return mapToResponseDTO(savedVehicleType);
  }

  public Page<VehicleTypeResponseDTO> getVehicleTypes(UUID tenantId, String search, Boolean isActive, Pageable pageable) {
    Page<VehicleType> page = vehicleTypeRepository.findByTenantIdWithFilter(tenantId, search, isActive, pageable);

    List<UUID> vehicleTypeIds = page.getContent().stream()
        .map(VehicleType::getId)
        .toList();

    Map<UUID, Long> vehicleCountByTypeId = new HashMap<>();
    if (!vehicleTypeIds.isEmpty()) {
      List<Object[]> rows = vehicleTypeRepository.countVehiclesGroupedByVehicleTypeId(tenantId, vehicleTypeIds);
      for (Object[] row : rows) {
        vehicleCountByTypeId.put((UUID) row[0], (Long) row[1]);
      }
    }

    return page.map(vehicleType ->
        mapToResponseDTO(vehicleType, vehicleCountByTypeId.getOrDefault(vehicleType.getId(), 0L)));
  }

  public VehicleTypeResponseDTO getVehicleTypeById(UUID tenantId, UUID id) {
    VehicleType vehicleType = vehicleTypeRepository.findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new AppException(ErrorCode.VEHICLE_TYPE_NOT_FOUND));

    return mapToResponseDTO(vehicleType);
  }

  private VehicleTypeResponseDTO mapToResponseDTO(VehicleType vehicleType) {
    long vehicleCount = vehicleTypeRepository.countVehiclesByTenantIdAndVehicleTypeId(vehicleType.getTenantId(), vehicleType.getId());

    return mapToResponseDTO(vehicleType, vehicleCount);
  }

  private VehicleTypeResponseDTO mapToResponseDTO(VehicleType vehicleType, long vehicleCount) {
    return VehicleTypeResponseDTO.builder()
        .id(vehicleType.getId())
        .tenantId(vehicleType.getTenantId())
        .name(vehicleType.getName())
        .description(vehicleType.getDescription())
        .isActive(vehicleType.getIsActive())
        .vehicleCount(vehicleCount)
        .createdAt(vehicleType.getCreatedAt())
        .updatedAt(vehicleType.getUpdatedAt())
        .build();
  }
}

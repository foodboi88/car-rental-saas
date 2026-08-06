package com.carrental.car_rental_backend.vehicle.service;

import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.vehicle.dto.CreateVehicleTypeRequestDTO;
import com.carrental.car_rental_backend.vehicle.dto.VehicleTypeResponseDTO;
import com.carrental.car_rental_backend.vehicle.entity.VehicleType;
import com.carrental.car_rental_backend.vehicle.repository.VehicleTypeRepository;
import org.springframework.stereotype.Service;

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

  private VehicleTypeResponseDTO mapToResponseDTO(VehicleType vehicleType) {
    long vehicleCount = vehicleTypeRepository.countVehiclesByTenantIdAndVehicleTypeId(vehicleType.getTenantId(), vehicleType.getId());

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

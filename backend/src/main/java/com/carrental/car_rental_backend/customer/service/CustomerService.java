package com.carrental.car_rental_backend.customer.service;

import com.carrental.car_rental_backend.customer.dto.ChangeRiskLevelRequestDTO;
import com.carrental.car_rental_backend.customer.dto.CreateCustomerRequestDTO;
import com.carrental.car_rental_backend.customer.dto.CustomerResponseDTO;
import com.carrental.car_rental_backend.customer.dto.UpdateCustomerRequestDTO;
import com.carrental.car_rental_backend.customer.repository.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CustomerService {
  private final CustomerRepository customerRepository;

  public CustomerService(CustomerRepository customerRepository) {
    this.customerRepository = customerRepository;
  }

  public CustomerResponseDTO createCustomer(UUID tenantId, CreateCustomerRequestDTO request) {
    // TODO: TASK-005
    return null;
  }

  public Page<CustomerResponseDTO> getCustomers(UUID tenantId, String search, Integer riskLevel, Pageable pageable) {
    // TODO: TASK-006
    return null;
  }

  public CustomerResponseDTO getCustomerById(UUID tenantId, UUID id) {
    // TODO: TASK-006
    return null;
  }

  public CustomerResponseDTO updateCustomer(UUID tenantId, UUID id, UpdateCustomerRequestDTO request) {
    // TODO: TASK-007
    return null;
  }

  public CustomerResponseDTO changeRiskLevel(UUID tenantId, UUID id, ChangeRiskLevelRequestDTO request) {
    // TODO: TASK-007
    return null;
  }

  public void deleteCustomer(UUID tenantId, UUID id) {
    // TODO: TASK-007;
  }
}

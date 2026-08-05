package com.carrental.car_rental_backend.branch.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.carrental.car_rental_backend.branch.entity.Branch;

@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {

}

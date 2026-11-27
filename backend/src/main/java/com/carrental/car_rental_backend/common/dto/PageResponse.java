package com.carrental.car_rental_backend.common.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@Builder 
@NoArgsConstructor 
@AllArgsConstructor 
public class PageResponse<T> {
  private List<T> items;
  private int pageNumber;
  private int pageSize;
  private long totalElements;
  private int totalPages;
  private boolean isLast;

  @JsonProperty("isLast")
  public boolean isLast() {
    return this.isLast;
  }

  public static <T> PageResponse<T> from(Page<T> page) {
    return PageResponse.<T>builder()
      .items(page.getContent())
      .pageNumber(page.getNumber())
      .pageSize(page.getSize())
      .totalElements(page.getTotalElements())
      .totalPages(page.getTotalPages())
      .isLast(page.isLast())
      .build();
  }
}

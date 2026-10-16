package com.carrental.car_rental_backend.common.enums.converter;

import java.util.Arrays;

import com.carrental.car_rental_backend.common.enums.BaseEnum;

import jakarta.persistence.AttributeConverter;

public class AbstractEnumConverter<E extends Enum<E> & BaseEnum<Y>, Y> implements AttributeConverter<E, Y> {

  private final Class<E> enumClass;

  public AbstractEnumConverter(Class<E> enumClass) {
    this.enumClass = enumClass;
  }

  @Override
  public Y convertToDatabaseColumn(E javaEnum) {
    if(javaEnum == null) return null;
    return javaEnum.getValue();
  }

  @Override
  public E convertToEntityAttribute(Y dbValue) {
    if(dbValue == null) return null;
    E[] listValueEnum = enumClass.getEnumConstants();

    return Arrays.stream(listValueEnum)
      .filter(item -> item.getValue().equals(dbValue))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Giá trị DB không hợp lệ:" + dbValue + " cho Enum: " + enumClass.getSimpleName()));
  }
  
}

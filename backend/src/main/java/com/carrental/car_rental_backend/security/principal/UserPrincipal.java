package com.carrental.car_rental_backend.security.principal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.carrental.car_rental_backend.account.entity.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class UserPrincipal implements UserDetails{
  private UUID id;
  private UUID tenantId;
  private String email;
  private String password;
  private String roleCode;
  private Collection<? extends GrantedAuthority> authorities;
  private boolean isActive;

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return this.authorities;
  }

  @Override
  public @Nullable String getPassword() {
    return this.password;
  }

  @Override
  public String getUsername() {
    return this.email;
  }

  @Override
  public boolean isEnabled() {
    return this.isActive;
  }

  @Override
  public boolean isAccountNonExpired() {
      return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
      return true;
  }

  @Override
  public boolean isAccountNonLocked() {
      return true;
  }

  public static UserPrincipal create(User user, UUID tenantId, String roleCode, List<String> permissions) {
    Collection<SimpleGrantedAuthority> formatedListPermission = new ArrayList<>();
    
    if(permissions != null && !permissions.isEmpty()){
      for(String item : permissions){
        formatedListPermission.add(new SimpleGrantedAuthority(item));
      }
    }

    if(roleCode != null && !roleCode.isBlank()){
      formatedListPermission.add(new SimpleGrantedAuthority("ROLE_" + roleCode));
    }

    return UserPrincipal.builder()
      .authorities(formatedListPermission)
      .email(user.getEmail())
      .id(user.getId())
      .isActive(user.getIsActive())
      .password(user.getPasswordHash())
      .roleCode(roleCode)
      .tenantId(tenantId)
      .build();
  }
  
}

package com.carrental.car_rental_backend.auth.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.carrental.car_rental_backend.auth.dto.response.UserSessionResponseDTO;
import com.carrental.car_rental_backend.auth.entity.UserSession;
import com.carrental.car_rental_backend.auth.repository.UserSessionRepository;
import com.carrental.car_rental_backend.auth.service.SessionManagerService;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.security.jwt.JwtProvider;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseSessionManagerImpl implements SessionManagerService {
  private final UserSessionRepository userSessionRepository;
  private final JwtProvider jwtProvider;
  @Value("${app.auth.max-active-sessions:5}") private int maxActiveSessions;
  @Value("${app.auth.last-active-throttle-minutes:5}") private long lastActiveThrottleMinutes;
  @Value("${app.auth.revoked-audit-retention-days:7}") private long revokedAuditRetentionDays;

  @Override
  @Transactional
  public UUID createSessionWithLru(UUID userId, String deviceName, String ipAddress) {
    Instant now = Instant.now();
    List<UserSession> listCurrentSessions = this.userSessionRepository.findByUserIdForLRUListFromOldestToNewest(userId, now);
    // Nếu như số lượng thiết bị đang hoạt động lớn hơn hoặc bằng 5
    // Revoke các thiết bị có thời gian hoạt động xa nhất, để lại 4 thiết bị  
    if (listCurrentSessions.size() >= this.maxActiveSessions) {
      int numberOfCutOffSession = listCurrentSessions.size() - this.maxActiveSessions + 1;
      for (int i = 0; i < numberOfCutOffSession; i++) {
          UserSession session = listCurrentSessions.get(i);
          session.setIsRevoked(true); // Chỉ cần gọi setter, KHÔNG cần gọi repository.save()
      }
    }

    Instant expiresAt = now.plusSeconds(this.jwtProvider.getRefreshTokenExpirationInSeconds());

    UserSession newUserSession = UserSession.builder()
      .userId(userId)
      .createdAt(now)
      .deviceName(deviceName)
      .expiresAt(expiresAt)
      .ipAddress(ipAddress)
      .lastActiveAt(now)
      .build();

    UserSession savedUserSession = this.userSessionRepository.save(newUserSession);

    return savedUserSession.getId();
  }

  @Override
  public boolean validateAndTouchSession(UUID sessionId, UUID userId) {
    if (sessionId == null || userId == null) return false;
    Optional<UserSession> userSession = this.userSessionRepository.findByIdAndUserId(sessionId, userId);

    if (userSession.isEmpty()) return false;

    Instant now = Instant.now();
    UserSession userSessionDetail = userSession.get();
    if (Boolean.TRUE.equals(userSessionDetail.getIsRevoked()) || userSessionDetail.getExpiresAt() == null || !userSessionDetail.getExpiresAt().isAfter(now)) return false;

    // 5 Phút mới thực hiện cập nhật last_active_at một lần. 
    // Để tránh bị 1 thiết bị call 5 api 1 lúc -> 5 thread chạy update 1 bản ghi session
    // Hold 1 lúc 5 connection tới DB một cách thừa thãi, để phần connection pool cho các thiết bị/user khác nữa
    // Thời gian mốc để kiểm tra last_active_at của session mà nhỏ hơn thì thực hiện cập nhật (tính bằng Thời điểm hiện tại - 5p)
    Instant threshold = now.minus(Duration.ofMinutes(this.lastActiveThrottleMinutes));
    if (
      userSessionDetail.getLastActiveAt() == null || // Nếu chưa có last_active_at thì cập nhật cho bằng hiện tại luôn
      userSessionDetail.getLastActiveAt().isBefore(threshold)
    ) {
      this.userSessionRepository.updateLastActiveIfStale(sessionId, now, threshold);
    }
    return true;
  }

  @Override
  @Transactional
  public void touchAndExtendSession(UUID sessionId, UUID userId) {
    if (sessionId == null || userId == null){
      throw new AppException(ErrorCode.UNAUTHORIZED, "Phiên đăng nhập trên thiết bị này đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại !");
    }
    
    Instant now = Instant.now();
    int numberOfUpdatedRow = this.userSessionRepository.extendExpiresTime(
      now,
      now.plusSeconds(this.jwtProvider.getRefreshTokenExpirationInSeconds()),
      sessionId,
      userId,
      now
    );

    if (numberOfUpdatedRow < 1) {
      throw new AppException(ErrorCode.UNAUTHORIZED, "Phiên đăng nhập trên thiết bị này đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại !");
    }
  }

  @Override
  @Transactional
  public boolean revokeSession(UUID userId, UUID sessionId) {
    if (userId == null || sessionId == null) return false;
    int numberOfRevokedSession = this.userSessionRepository.revokeByIdAndUserId(sessionId, userId);
    return numberOfRevokedSession > 0;
  }

  @Override
  @Transactional
  public void revokeDeviceSession(UUID userId, UUID currentSessionId, UUID targetSessionId) {
    if (userId == null || targetSessionId == null) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
    if (java.util.Objects.equals(currentSessionId, targetSessionId)) throw new AppException(ErrorCode.BAD_REQUEST, "Không thể thu hồi phiên hiện tại. Vui lòng sử dụng chức năng Đăng xuất!");

    int numberOfRevokedSession = this.userSessionRepository.revokeByIdAndUserId(targetSessionId, userId);
    if (numberOfRevokedSession < 1 ) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phiên đăng nhập cần thu hồi!");
  }

  @Override
  @Transactional
  public int revokeAllOtherSessions(UUID userId, UUID currentSessionId) {
    if (userId == null || currentSessionId == null) return 0;
    int numberOfRevokedSession = this.userSessionRepository.revokeAllOtherSessions(userId, currentSessionId);
    return numberOfRevokedSession;
  }

  @Override
  @Transactional
  public int revokeAllByUserId(UUID userId) {
    if (userId == null) return 0;
    int numberOfRevokedSession = this.userSessionRepository.revokeAllByUserId(userId);
    return numberOfRevokedSession;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserSessionResponseDTO> getActiveSessions(UUID userId, UUID currentSessionId) {
    if (userId == null) return List.of();
    Instant now = Instant.now();
    List<UserSession> activeUserSessionList = this.userSessionRepository.findByUserIdForLRUFromNewestToOldest(userId, now);
    List<UserSessionResponseDTO> responseDTO = activeUserSessionList.stream().map(session -> {
      return UserSessionResponseDTO.builder()
      .id(session.getId())
      .createdAt(session.getCreatedAt())
      .deviceName(session.getDeviceName())
      .expiresAt(session.getExpiresAt())
      .ipAddress(session.getIpAddress())
      .isCurrent(java.util.Objects.equals(currentSessionId, session.getId()))
      .lastActiveAt(session.getLastActiveAt())
      .build();
    }).toList();
    return responseDTO;
  }

  @Override
  @Transactional
  public int cleanupExpiredAndOldRevokedSessions() {
    Instant auditCutOff = Instant.now().minus(Duration.ofDays(this.revokedAuditRetentionDays));
    int numberOfCleanedSession = this.userSessionRepository.deleteExpiredAndOldRevokedSessions(auditCutOff);
    return numberOfCleanedSession;
  }
  
}

package com.carrental.car_rental_backend.auth.service;

import java.util.List;
import java.util.UUID;

import com.carrental.car_rental_backend.auth.dto.response.UserSessionResponseDTO;

public interface SessionManagerService {
  UUID createSessionWithLru(UUID userId, String deviceName, String ipAddress);
  boolean validateAndTouchSession(UUID sessionId, UUID userId);
  void touchAndExtendSession(UUID sessionId, UUID userId);
  //Dùng cho logout: trả về true nếu có phiên bị thu hồi, false nếu không tìm thấy/đã thu hồi
  boolean revokeSession(UUID userId, UUID sessionId);
  //Dùng cho DELETE /sessions/{sessionId}: chặn self-revoke ném 400 BAD_REQUEST, gọi revokeByIdAndUserId == 0 ném 404 RESOURCE_NOT_FOUND để chống IDOR — giúp Controller không chứa logic if/else!
  void revokeDeviceSession(UUID userId, UUID currentSessionId, UUID targetSessionId);
  int revokeAllOtherSessions(UUID userId, UUID currentSessionId);
  int revokeAllByUserId(UUID userId);
  List<UserSessionResponseDTO> getActiveSessions(UUID userId, UUID currentSessionId);
  int cleanupExpiredAndOldRevokedSessions();
}

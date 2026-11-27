package com.carrental.car_rental_backend.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.carrental.car_rental_backend.auth.entity.UserSession;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
  //Mục đích: Lấy phiên theo cặp (id, userId) — chống IDOR ngay từ tầng SQL, mặc định chạy read-only trong Spring Data JPA.
  @Transactional(readOnly = true)
  Optional<UserSession> findByIdAndUserId(UUID id, UUID userId);

  //Phục vụ thuật toán LRU khi login (sắp xếp từ cũ nhất đến mới nhất trong các thiết bị chưa bị revoke + còn hạn chưa expire)
  @Query(value = """
    SELECT us.* FROM user_sessions AS us
    WHERE 
      us.user_id = :userId AND
      us.is_revoked = false AND
      us.expires_at > :now
    ORDER BY
      us.last_active_at ASC,
      us.created_at ASC
  """, nativeQuery = true) // Phải thêm điều kiện sắp xếp us.created_at khi mà có 2 us cùng last_active_at
  List<UserSession> findByUserIdForLRUListFromOldestToNewest(@Param("userId") UUID userId, @Param("now") Instant now);

  //Phục vụ API GET /api/v1/auth/sessions hiển thị thiết bị mới dùng nhất lên đầu.
  @Query(value = """
    SELECT us.* FROM user_sessions AS us
    WHERE 
      us.user_id = :userId AND
      us.is_revoked = false AND
      us.expires_at > :now
    ORDER BY
      us.last_active_at DESC
  """, nativeQuery = true)
  List<UserSession> findByUserIdForLRUFromNewestToOldest(@Param("userId") UUID userId, @Param("now") Instant now);

  @Transactional
  @Modifying(clearAutomatically = true)
  @Query(value = """
    UPDATE user_sessions AS us
    SET last_active_at = :now
    WHERE us.id = :id AND us.is_revoked = false AND us.last_active_at < :threshold
  """, nativeQuery = true)
  int updateLastActiveIfStale(@Param("id") UUID id, @Param("now") Instant now, @Param("threshold") Instant threshold);

  @Modifying(clearAutomatically = true)
  @Query(value = """
    UPDATE user_sessions AS us
    SET is_revoked = true
    WHERE us.id = :id AND us.user_id = :userId AND us.is_revoked = false    
  """, nativeQuery = true)
  int revokeByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

  @Modifying(clearAutomatically = true)
  @Query(value = """
    UPDATE user_sessions AS us
    SET is_revoked = true
    WHERE 
      us.user_id = :userId AND
      us.id != :currentSessionId AND
      us.is_revoked = false
  """, nativeQuery = true)
  int revokeAllOtherSessions(@Param("userId") UUID userId, @Param("currentSessionId") UUID currentSessionId);

  @Modifying(clearAutomatically = true)
  @Query(value = """
    UPDATE user_sessions AS us
    SET is_revoked = true
    WHERE
      us.user_id = :userId AND
      us.is_revoked = false
  """, nativeQuery = true)
  int revokeAllByUserId(@Param("userId") UUID userId);

  // Xóa phiên thiết bị 
  // - đã hết hạn được 7 ngày
  // - đã đăng xuất/vô hiệu hóa + lần cuối hoạt động là hơn 7 ngày trước
  @Modifying(clearAutomatically = true)
  @Query(value = """
    DELETE FROM user_sessions AS us
    WHERE 
      us.expires_at < :auditCutOff OR
      (us.is_revoked = true AND us.last_active_at < :auditCutOff)
  """, nativeQuery = true)
  int deleteExpiredAndOldRevokedSessions(@Param("auditCutOff") Instant auditCutOff);

  @Modifying(clearAutomatically = true)
  @Query(value = """
    UPDATE user_sessions AS us
    SET 
      last_active_at = :newLastActiveAt,
      expires_at = :newExpiresAt
    WHERE 
      us.id = :sessionId AND
      us.user_id = :userId AND
      us.is_revoked = false AND
      us.expires_at > :now
  """, nativeQuery = true)
  int extendExpiresTime(
    @Param("newLastActiveAt") Instant newLastActiveAt,
    @Param("newExpiresAt") Instant newExpiresAt,
    @Param("sessionId") UUID sessionId,
    @Param("userId") UUID userId,
    @Param("now") Instant now
  );
}

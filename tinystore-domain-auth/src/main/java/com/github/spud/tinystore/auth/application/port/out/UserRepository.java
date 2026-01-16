package com.github.spud.tinystore.auth.application.port.out;

import com.github.spud.tinystore.auth.domain.model.user.MallUser;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import java.util.Optional;

/**
 * 用户持久化端口（auth 域用户本地库，user_id 对齐 account，phone 唯一）
 */
public interface UserRepository {

  Optional<MallUser> findById(UserId userId);

  Optional<MallUser> findByPhone(PhoneNumber phone);

  void save(MallUser user);

  void update(MallUser user);

  void updatePassword(UserId userId, String passwordHash);

  void updateStatus(UserId userId, int accountStatus);
}

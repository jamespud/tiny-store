package com.github.spud.tinystore.auth.infrastructure.persistence;

import com.github.spud.tinystore.auth.application.port.out.UserRepository;
import com.github.spud.tinystore.auth.domain.model.user.MallUser;
import com.github.spud.tinystore.auth.domain.model.user.MallUserStatus;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import com.github.spud.tinystore.auth.domain.primitives.RtVersion;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import com.github.spud.tinystore.auth.infrastructure.persistence.entity.AuthUserEntity;
import com.github.spud.tinystore.auth.infrastructure.persistence.repository.AuthUserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaUserRepositoryAdapter implements UserRepository {

  private final AuthUserJpaRepository jpaRepository;

  @Override
  public Optional<MallUser> findById(UserId userId) {
    return jpaRepository.findById(Long.parseLong(userId.value()))
        .map(this::toDomain);
  }

  @Override
  public Optional<MallUser> findByPhone(PhoneNumber phone) {
    return jpaRepository.findByPhone(phone.value())
        .map(this::toDomain);
  }

  @Override
  @Transactional
  public void save(MallUser user) {
    AuthUserEntity entity = toEntity(user);
    jpaRepository.save(entity);
  }

  @Override
  @Transactional
  public void update(MallUser user) {
    jpaRepository.findById(Long.parseLong(user.getId().value()))
        .ifPresent(existing -> {
          existing.setPhone(user.getPhone().value());
          existing.setPassword(user.getPassword());
          existing.setAccountStatus(statusToInt(user.getStatus()));
          jpaRepository.save(existing);
        });
  }

  @Override
  @Transactional
  public void updatePassword(UserId userId, String passwordHash) {
    jpaRepository.findById(Long.parseLong(userId.value()))
        .ifPresent(entity -> {
          entity.setPassword(passwordHash);
          jpaRepository.save(entity);
        });
  }

  @Override
  @Transactional
  public void updateStatus(UserId userId, int accountStatus) {
    jpaRepository.findById(Long.parseLong(userId.value()))
        .ifPresent(entity -> {
          entity.setAccountStatus(accountStatus);
          jpaRepository.save(entity);
        });
  }

  private MallUser toDomain(AuthUserEntity entity) {
    return MallUser.restore(
        UserId.of(String.valueOf(entity.getUserId())),
        PhoneNumber.of(entity.getPhone()),
        null,  // username/nickname 暂不在 auth_user 存储
        null,  // avatar 暂不在 auth_user 存储
        entity.getPassword(),
        intToStatus(entity.getAccountStatus()),
        RtVersion.of(1)  // rtVersion 后续可补字段，当前默认 1
    );
  }

  private AuthUserEntity toEntity(MallUser user) {
    AuthUserEntity entity = new AuthUserEntity();
    entity.setUserId(Long.parseLong(user.getId().value()));
    entity.setPhone(user.getPhone().value());
    entity.setPassword(user.getPassword());
    entity.setAccountStatus(statusToInt(user.getStatus()));
    return entity;
  }

  private MallUserStatus intToStatus(int status) {
    return status == 1 ? MallUserStatus.ACTIVE : MallUserStatus.FROZEN;
  }

  private int statusToInt(MallUserStatus status) {
    return status == MallUserStatus.ACTIVE ? 1 : 0;
  }
}

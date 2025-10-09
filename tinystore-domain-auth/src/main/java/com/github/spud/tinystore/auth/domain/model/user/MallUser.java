package com.github.spud.tinystore.auth.domain.model.user;

import com.github.spud.tinystore.auth.domain.exception.UserFrozenException;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import com.github.spud.tinystore.auth.domain.primitives.RtVersion;
import com.github.spud.tinystore.auth.domain.primitives.UserId;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Mall 用户聚合根。
 */
public class MallUser {

	private final UserId id;
	private final PhoneNumber phone;
	private String nickname;
	private String avatar;
	private MallUserStatus status;
	private RtVersion rtVersion;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;

	private MallUser(UserId id,
	                 PhoneNumber phone,
	                 String nickname,
	                 String avatar,
	                 MallUserStatus status,
	                 RtVersion rtVersion,
	                 OffsetDateTime createdAt,
	                 OffsetDateTime updatedAt) {
		this.id = Objects.requireNonNull(id, "id");
		this.phone = Objects.requireNonNull(phone, "phone");
		this.nickname = nickname;
		this.avatar = avatar;
		this.status = Objects.requireNonNullElse(status, MallUserStatus.ACTIVE);
		this.rtVersion = Objects.requireNonNullElse(rtVersion, RtVersion.of(1));
		this.createdAt = Objects.requireNonNullElse(createdAt, OffsetDateTime.now());
		this.updatedAt = Objects.requireNonNullElse(updatedAt, this.createdAt);
	}

	public static MallUser register(UserId id, PhoneNumber phone, String nickname, String avatar, OffsetDateTime now) {
		return new MallUser(id, phone, nickname, avatar, MallUserStatus.ACTIVE, RtVersion.of(1), now, now);
	}

	public static MallUser restore(UserId id, PhoneNumber phone, String nickname, String avatar,
	                               MallUserStatus status, RtVersion version,
	                               OffsetDateTime createdAt, OffsetDateTime updatedAt) {
		return new MallUser(id, phone, nickname, avatar, status, version, createdAt, updatedAt);
	}

	public void freeze() {
		this.status = MallUserStatus.FROZEN;
	}

	public void unfreeze() {
		this.status = MallUserStatus.ACTIVE;
	}

	public void ensureActive() {
		if (status == MallUserStatus.FROZEN) {
			throw new UserFrozenException("mall user is frozen");
		}
	}

	public void updateProfile(String nickname, String avatar) {
		this.nickname = nickname;
		this.avatar = avatar;
		this.updatedAt = OffsetDateTime.now();
	}

	public RtVersion bumpRtVersion() {
		this.rtVersion = this.rtVersion.next();
		this.updatedAt = OffsetDateTime.now();
		return this.rtVersion;
	}

	public UserId getId() {
		return id;
	}

	public PhoneNumber getPhone() {
		return phone;
	}

	public String getNickname() {
		return nickname;
	}

	public String getAvatar() {
		return avatar;
	}

	public MallUserStatus getStatus() {
		return status;
	}

	public RtVersion getRtVersion() {
		return rtVersion;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

	public boolean isFrozen() {
		return status == MallUserStatus.FROZEN;
	}
}
package com.github.spud.tinystore.auth.domain.user;

import java.time.OffsetDateTime;
import java.util.UUID;

public class MallUser {

	private UUID id;
	private String phone;
	private String nickname;
	private String avatar;
	private String status;
	private int rtVersion;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;

	public MallUser() {
	}

	public MallUser(UUID id, String phone, String nickname, String avatar, String status,
	                int rtVersion, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
		this.id = id;
		this.phone = phone;
		this.nickname = nickname;
		this.avatar = avatar;
		this.status = status;
		this.rtVersion = rtVersion;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getNickname() {
		return nickname;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	public String getAvatar() {
		return avatar;
	}

	public void setAvatar(String avatar) {
		this.avatar = avatar;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public int getRtVersion() {
		return rtVersion;
	}

	public void setRtVersion(int rtVersion) {
		this.rtVersion = rtVersion;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(OffsetDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(OffsetDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public boolean isFrozen() {
		return "frozen".equalsIgnoreCase(this.status);
	}
}

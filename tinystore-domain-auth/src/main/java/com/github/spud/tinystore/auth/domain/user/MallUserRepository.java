package com.github.spud.tinystore.auth.domain.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MallUserRepository {

	private static final RowMapper<MallUser> ROW_MAPPER = (rs, rowNum) -> new MallUser(
		(UUID) rs.getObject("id"),
		rs.getString("phone"),
		rs.getString("nickname"),
		rs.getString("avatar"),
		rs.getString("status"),
		rs.getInt("rt_version"),
		rs.getObject("created_at", OffsetDateTime.class),
		rs.getObject("updated_at", OffsetDateTime.class)
	);

	private final JdbcTemplate jdbcTemplate;

	public MallUserRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<MallUser> findByPhone(String phone) {
		return jdbcTemplate.query("select * from mall_user where phone = ?", ROW_MAPPER, phone)
			.stream().findFirst();
	}

	public Optional<MallUser> findById(UUID id) {
		return jdbcTemplate.query("select * from mall_user where id = ?", ROW_MAPPER, id)
			.stream().findFirst();
	}

	public MallUser save(MallUser user) {
		UUID id = user.getId() != null ? user.getId() : UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		int rtVersion = user.getRtVersion() > 0 ? user.getRtVersion() : 1;
		jdbcTemplate.update(con -> {
			PreparedStatement ps = con.prepareStatement(
				"insert into mall_user (id, phone, nickname, avatar, status, rt_version, created_at, updated_at) " +
					"values (?, ?, ?, ?, ?, ?, ?, ?)");
			ps.setObject(1, id);
			ps.setString(2, user.getPhone());
			ps.setString(3, user.getNickname());
			ps.setString(4, user.getAvatar());
			ps.setString(5, user.getStatus() != null ? user.getStatus() : "normal");
			ps.setInt(6, rtVersion);
			ps.setObject(7, now);
			ps.setObject(8, now);
			return ps;
		});
		return findById(id).orElseThrow();
	}

	public void updateRtVersion(UUID id, int newVersion) {
		jdbcTemplate.update("update mall_user set rt_version = ?, updated_at = now() where id = ?", newVersion, id);
	}

	public void updateProfile(UUID id, String nickname, String avatar) {
		jdbcTemplate.update("update mall_user set nickname = ?, avatar = ?, updated_at = now() where id = ?",
			nickname, avatar, id);
	}
}

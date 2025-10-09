package com.github.spud.tinystore.auth.infrastructure.persistence.jdbc;

import com.github.spud.tinystore.auth.application.port.out.UserRepository;
import com.github.spud.tinystore.auth.domain.model.user.MallUser;
import com.github.spud.tinystore.auth.domain.model.user.MallUserStatus;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import com.github.spud.tinystore.auth.domain.primitives.RtVersion;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class JdbcUserRepositoryAdapter implements UserRepository {

	private static final RowMapper<MallUser> ROW_MAPPER = (rs, rowNum) -> MallUser.restore(
		UserId.of(rs.getObject("id").toString()),
		PhoneNumber.of(rs.getString("phone")),
		rs.getString("nickname"),
		rs.getString("avatar"),
		mapStatus(rs.getString("status")),
		RtVersion.of(rs.getInt("rt_version")),
		rs.getObject("created_at", OffsetDateTime.class),
		rs.getObject("updated_at", OffsetDateTime.class)
	);

	private final JdbcTemplate jdbcTemplate;

	public JdbcUserRepositoryAdapter(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<MallUser> findByPhone(PhoneNumber phone) {
		return jdbcTemplate.query("select * from mall_user where phone = ?", ROW_MAPPER, phone.getValue())
			.stream()
			.findFirst();
	}

	@Override
	public Optional<MallUser> findById(UserId id) {
		return jdbcTemplate.query("select * from mall_user where id = ?", ROW_MAPPER, id.getValue())
			.stream()
			.findFirst();
	}

	@Override
	public MallUser save(MallUser user) {
		var now = OffsetDateTime.now();
		jdbcTemplate.update(con -> {
			PreparedStatement ps = con.prepareStatement(
				"insert into mall_user (id, phone, nickname, avatar, status, rt_version, created_at, updated_at) " +
					"values (?, ?, ?, ?, ?, ?, ?, ?)");
			ps.setObject(1, user.getId().getValue());
			ps.setString(2, user.getPhone().getValue());
			ps.setString(3, user.getNickname());
			ps.setString(4, user.getAvatar());
			ps.setString(5, toColumnStatus(user.getStatus()));
			ps.setInt(6, (int) user.getRtVersion().getValue());
			ps.setObject(7, now);
			ps.setObject(8, now);
			return ps;
		});
		return findById(user.getId()).orElseThrow();
	}

	@Override
	public MallUser update(MallUser user) {
		jdbcTemplate.update(
			"update mall_user set phone = ?, nickname = ?, avatar = ?, status = ?, rt_version = ?, updated_at = now() where id = ?",
			user.getPhone().getValue(),
			user.getNickname(),
			user.getAvatar(),
			toColumnStatus(user.getStatus()),
			user.getRtVersion().getValue(),
			user.getId().getValue()
		);
		return findById(user.getId()).orElseThrow();
	}

	@Override
	public void updateRtVersion(String id, RtVersion nextVersion, RtVersion expectedVersion) {
		jdbcTemplate.update(
			"update mall_user set rt_version = ? where id = ? and  rt_version = ?",
			nextVersion.getValue(),
			id,
			expectedVersion.getValue()
		);
	}

	private static MallUserStatus mapStatus(String status) {
		if (status == null) {
			return MallUserStatus.ACTIVE;
		}
		return "frozen".equalsIgnoreCase(status) ? MallUserStatus.FROZEN : MallUserStatus.ACTIVE;
	}

	private static String toColumnStatus(MallUserStatus status) {
		return status == MallUserStatus.FROZEN ? "frozen" : "normal";
	}
}
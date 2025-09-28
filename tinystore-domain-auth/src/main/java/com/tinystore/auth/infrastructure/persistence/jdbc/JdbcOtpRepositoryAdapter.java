package com.tinystore.auth.infrastructure.persistence.jdbc;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.tinystore.auth.application.port.out.OtpRepositoryPort;
import com.tinystore.auth.domain.model.otp.Otp;
import com.tinystore.auth.domain.primitives.OtpCode;
import com.tinystore.auth.domain.primitives.PhoneNumber;

@Repository
public class JdbcOtpRepositoryAdapter implements OtpRepositoryPort {

	private static final RowMapper<Otp> ROW_MAPPER = (rs, rowNum) -> new Otp(
		(UUID) rs.getObject("id"),
		PhoneNumber.of(rs.getString("phone")),
		OtpCode.of(rs.getString("code")),
		rs.getObject("expire_at", OffsetDateTime.class),
		rs.getBoolean("used"),
		rs.getObject("used_at", OffsetDateTime.class)
	);

	private final JdbcTemplate jdbcTemplate;

	public JdbcOtpRepositoryAdapter(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Otp save(Otp otp) {
		jdbcTemplate.update(con -> {
			var ps = con.prepareStatement("insert into login_otp (id, phone, code, expire_at, used, created_at) values (?, ?, ?, ?, ?, now())");
			ps.setObject(1, otp.getId());
			ps.setString(2, otp.getPhone().getValue());
			ps.setString(3, otp.getCode().getValue());
			ps.setObject(4, otp.getExpireAt());
			ps.setBoolean(5, otp.isUsed());
			return ps;
		});
		return otp;
	}

	@Override
	public Optional<Otp> findLatest(PhoneNumber phone) {
		return jdbcTemplate.query(
			"select id, phone, code, expire_at, used, used_at from login_otp where phone = ? and used = false order by created_at desc limit 1",
			ROW_MAPPER,
			phone.getValue())
			.stream()
			.findFirst();
	}

	@Override
	public void markUsed(Otp otp) {
		jdbcTemplate.update("update login_otp set used = true, used_at = now() where id = ?", otp.getId());
	}
}
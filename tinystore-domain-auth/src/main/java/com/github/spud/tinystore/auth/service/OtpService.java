package com.github.spud.tinystore.auth.service;

import com.github.spud.tinystore.auth.infrastructure.SmsStubClient;
import com.github.spud.tinystore.auth.service.exception.InvalidOtpException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class OtpService {

	private static final RowMapper<OtpRecord> ROW_MAPPER = (rs, rowNum) -> new OtpRecord(
		(UUID) rs.getObject("id"),
		rs.getString("phone"),
		rs.getString("code"),
		rs.getObject("expire_at", OffsetDateTime.class),
		rs.getBoolean("used")
	);

	private final JdbcTemplate jdbcTemplate;
	private final SmsStubClient smsStubClient;
	private final Duration ttl;

	public OtpService(JdbcTemplate jdbcTemplate,
	                  SmsStubClient smsStubClient,
	                  @Value("${tinystore.auth.otp.ttl:PT5M}") Duration ttl) {
		this.jdbcTemplate = jdbcTemplate;
		this.smsStubClient = smsStubClient;
		this.ttl = ttl;
	}

	@Transactional
	public void sendCode(String phone) {
		String code = smsStubClient.sendLoginCode(phone);
		OffsetDateTime expireAt = OffsetDateTime.now().plus(ttl);
		jdbcTemplate.update(con -> {
			var ps = con.prepareStatement(
				"insert into login_otp (id, phone, code, expire_at, used, created_at) values (?, ?, ?, ?, false, now())");
			ps.setObject(1, UUID.randomUUID());
			ps.setString(2, phone);
			ps.setString(3, code);
			ps.setObject(4, expireAt);
			return ps;
		});
	}

	@Transactional
	public void verifyCode(String phone, String code) {
		OtpRecord record = jdbcTemplate.query(
				"select * from login_otp where phone = ? and used = false order by created_at desc limit 1",
				ROW_MAPPER, phone)
			.stream()
			.findFirst()
			.orElseThrow(() -> new InvalidOtpException("验证码不存在或已失效"));
		if (record.expireAt().isBefore(OffsetDateTime.now())) {
			markUsed(record.id());
			throw new InvalidOtpException("验证码已过期");
		}
		if (!record.code().equals(code)) {
			throw new InvalidOtpException("验证码错误");
		}
		markUsed(record.id());
	}

	private void markUsed(UUID id) {
		jdbcTemplate.update("update login_otp set used = true where id = ?", id);
	}

	private record OtpRecord(UUID id, String phone, String code, OffsetDateTime expireAt, boolean used) {
	}
}

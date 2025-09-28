package com.github.spud.tinystore.auth.service;

import com.github.spud.tinystore.auth.domain.user.MallUser;
import com.github.spud.tinystore.auth.domain.user.MallUserRepository;
import com.github.spud.tinystore.auth.service.exception.AccountFrozenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

	private final MallUserRepository repository;

	public UserService(MallUserRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public MallUser getOrCreateByPhone(String phone) {
		return repository.findByPhone(phone)
			.map(this::ensureNotFrozen)
			.orElseGet(() -> {
				MallUser user = new MallUser();
				user.setPhone(phone);
				user.setNickname("用户" + phone.substring(Math.max(phone.length() - 4, 0)));
				user.setStatus("normal");
				user.setRtVersion(1);
				user.setCreatedAt(OffsetDateTime.now());
				user.setUpdatedAt(OffsetDateTime.now());
				return ensureNotFrozen(repository.save(user));
			});
	}

	public Optional<MallUser> findById(UUID id) {
		return repository.findById(id);
	}

	@Transactional
	public int incrementRtVersion(UUID id) {
		MallUser user = repository.findById(id).orElseThrow();
		int nextVersion = user.getRtVersion() + 1;
		repository.updateRtVersion(id, nextVersion);
		return nextVersion;
	}

	private MallUser ensureNotFrozen(MallUser user) {
		if (user.isFrozen()) {
			throw new AccountFrozenException("mall user is frozen");
		}
		return user;
	}
}

package com.github.spud.tinystore.auth.domain.service;

import com.github.spud.tinystore.auth.application.port.out.UserRepository;
import com.github.spud.tinystore.auth.domain.exception.AccountFrozenException;
import com.github.spud.tinystore.auth.domain.model.user.MallUser;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import com.github.spud.tinystore.auth.domain.primitives.RtVersion;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

	private final UserRepository repository;

	public UserService(UserRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public MallUser getOrCreateByPhone(String phone) {
		return repository.findByPhone(PhoneNumber.of(phone))
			.map(this::ensureNotFrozen)
			.orElseGet(() -> {
//				MallUser user = new MallUser();
//				user.setPhone(phone);
//				user.setNickname("用户" + phone.substring(Math.max(phone.length() - 4, 0)));
//				user.setStatus("normal");
//				user.setRtVersion(1);
//				user.setCreatedAt(OffsetDateTime.now());
//				user.setUpdatedAt(OffsetDateTime.now());
//				return ensureNotFrozen(repository.save(user));
				return null;
			}
			);
	}

	public Optional<MallUser> findById(String id) {
		return repository.findById(UserId.of(id));
	}

	@Transactional
	public long incrementRtVersion(String id) {
		MallUser user = repository.findById(UserId.of(id)).orElseThrow();
		RtVersion rtVersion = user.getRtVersion();
		RtVersion nextVersion = rtVersion.next();
		repository.updateRtVersion(id, nextVersion, rtVersion);
		return nextVersion.getValue();
	}

	private MallUser ensureNotFrozen(MallUser user) {
		if (user.isFrozen()) {
			throw new AccountFrozenException("mall user is frozen");
		}
		return user;
	}
}

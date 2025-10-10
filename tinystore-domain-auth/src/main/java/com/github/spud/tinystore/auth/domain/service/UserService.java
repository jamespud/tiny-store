package com.github.spud.tinystore.auth.domain.service;

import com.github.spud.tinystore.auth.application.port.out.UserRepository;
import com.github.spud.tinystore.auth.domain.exception.AccountFrozenException;
import com.github.spud.tinystore.auth.domain.model.user.MallUser;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import com.github.spud.tinystore.auth.domain.primitives.RtVersion;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

//@Service
public class UserService implements UserDetailsService {

	private final UserRepository repository;

	public UserService(UserRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public MallUser getOrCreateByPhone(String phone) {
		return repository.findByPhone(PhoneNumber.of(phone))
			.map(this::ensureNotFrozen)
			.orElseGet(() -> MallUser.register(UserId.random(), PhoneNumber.of(phone),
				"用户" + phone.substring(Math.max(phone.length() - 4, 0)), "default")
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
		return nextVersion.value();
	}

	private MallUser ensureNotFrozen(MallUser user) {
		if (user.isFrozen()) {
			throw new AccountFrozenException("mall user is frozen");
		}
		return user;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		return null;
	}

}

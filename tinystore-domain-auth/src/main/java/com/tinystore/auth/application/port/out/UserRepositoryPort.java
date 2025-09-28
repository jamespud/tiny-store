package com.tinystore.auth.application.port.out;

import java.util.Optional;

import com.tinystore.auth.domain.model.user.MallUser;
import com.tinystore.auth.domain.primitives.PhoneNumber;
import com.tinystore.auth.domain.primitives.UserId;

public interface UserRepositoryPort {

	Optional<MallUser> findByPhone(PhoneNumber phone);

	Optional<MallUser> findById(UserId id);

	MallUser save(MallUser user);

	MallUser update(MallUser user);
}
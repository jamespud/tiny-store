package com.github.spud.tinystore.auth.application.port.out;

import com.github.spud.tinystore.auth.domain.model.user.MallUser;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import com.github.spud.tinystore.auth.domain.primitives.RtVersion;
import com.github.spud.tinystore.auth.domain.primitives.UserId;

import java.util.Optional;

public interface UserRepository {

	Optional<MallUser> findByPhone(PhoneNumber phone);

	Optional<MallUser> findById(UserId id);

	MallUser save(MallUser user);

	MallUser update(MallUser user);

	void updateRtVersion(String id, RtVersion nextVersion, RtVersion expectedVersion);
}
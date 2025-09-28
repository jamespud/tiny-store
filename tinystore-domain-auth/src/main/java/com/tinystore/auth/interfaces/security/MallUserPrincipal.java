package com.tinystore.auth.interfaces.security;

import com.tinystore.auth.application.dto.MallUserView;
import com.tinystore.auth.domain.model.user.MallUserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class MallUserPrincipal implements UserDetails {

	private final MallUserView user;
	private final Collection<? extends GrantedAuthority> authorities;

	public MallUserPrincipal(MallUserView user, Collection<? extends GrantedAuthority> authorities) {
		this.user = user;
		this.authorities = authorities;
	}

	public MallUserView getUser() {
		return user;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getPassword() {
		return null;
	}

	@Override
	public String getUsername() {
		return user.id().toString();
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return user.status() != MallUserStatus.FROZEN;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return user.status() != MallUserStatus.FROZEN;
	}
}

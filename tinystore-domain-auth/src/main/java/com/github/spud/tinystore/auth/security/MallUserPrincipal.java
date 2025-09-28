package com.github.spud.tinystore.auth.security;

import com.github.spud.tinystore.auth.domain.user.MallUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class MallUserPrincipal implements UserDetails {

	private final MallUser user;
	private final Collection<? extends GrantedAuthority> authorities;

	public MallUserPrincipal(MallUser user, Collection<? extends GrantedAuthority> authorities) {
		this.user = user;
		this.authorities = authorities;
	}

	public MallUser getUser() {
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
		return user.getId().toString();
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return !user.isFrozen();
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return !user.isFrozen();
	}
}

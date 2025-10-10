package com.github.spud.tinystore.auth.infrastructure.acl;

public interface SmsSender {

	void send(String phone, String content);
}
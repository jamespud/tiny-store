package com.github.spud.tinystore.account.infrastructure.id;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;

/**
 * 简化的雪花ID生成器（String格式）
 * 结构：41位时间戳 + 10位机器ID + 12位序列号
 */
@Component
public class UserIdGenerator {

    private static final long EPOCH = 1640995200000L; // 2022-01-01 00:00:00 UTC
    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public UserIdGenerator() {
        this.workerId = generateWorkerId();
    }

    public synchronized String generate() {
        long timestamp = timeGen();

        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate id");
        }

        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        long id = ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;

        return String.valueOf(id);
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }

    private long generateWorkerId() {
        try {
            InetAddress ip = InetAddress.getLocalHost();
            byte[] ipBytes = ip.getAddress();
            return ((ipBytes[ipBytes.length - 2] & 0xFF) << 8 
                    | (ipBytes[ipBytes.length - 1] & 0xFF)) & MAX_WORKER_ID;
        } catch (UnknownHostException e) {
            return new SecureRandom().nextInt((int) MAX_WORKER_ID);
        }
    }
}

package com.qiji.cps.framework.redis.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * 安全的 Redis JSON 序列化器
 * <p>
 * 包装底层的 JSON 序列化器，增加容错与优雅降级能力：
 * 1. 兼容性保护：若待反序列化的字节以 Java 序列化魔数 0xAC 0xED 开头
 *    （如 Spring Cache 历史写入的 NullValue.INSTANCE 标记，或旧系统的 JDK 序列化数据），
 *    避免抛出 {@code Unexpected character ('¬' (code 172))}，直接返回 null 视作缓存未命中回源。
 * 2. 脏数据容错：遇到损坏或不兼容的缓存数据时，记录告警日志并返回 null，保证核心业务不被脏缓存打断。
 */
@Slf4j
public class SafeRedisJsonSerializer implements RedisSerializer<Object> {

    private final RedisSerializer<Object> delegate;

    public SafeRedisJsonSerializer(RedisSerializer<Object> delegate) {
        this.delegate = delegate;
    }

    @Override
    public byte[] serialize(Object value) throws SerializationException {
        return delegate.serialize(value);
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        // 检测 Java 原生序列化魔数 STREAM_MAGIC (0xaced)
        if (isJdkSerialization(bytes)) {
            log.warn("[SafeRedisJsonSerializer] 检测到 JDK 原生序列化字节或 Spring Cache NullValue 标记，自动降级为缓存未命中");
            return null;
        }
        try {
            return delegate.deserialize(bytes);
        } catch (SerializationException e) {
            log.warn("[SafeRedisJsonSerializer] 反序列化缓存数据失败，自动降级为缓存未命中: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> type) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        if (isJdkSerialization(bytes)) {
            log.warn("[SafeRedisJsonSerializer] 检测到 JDK 原生序列化字节或 Spring Cache NullValue 标记，自动降级为缓存未命中");
            return null;
        }
        try {
            return delegate.deserialize(bytes, type);
        } catch (SerializationException e) {
            log.warn("[SafeRedisJsonSerializer] 反序列化缓存数据失败，自动降级为缓存未命中: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean canSerialize(Class<?> type) {
        return delegate.canSerialize(type);
    }

    @Override
    public Class<?> getTargetType() {
        return delegate.getTargetType();
    }

    private static boolean isJdkSerialization(byte[] bytes) {
        return bytes.length >= 2 && bytes[0] == (byte) 0xAC && bytes[1] == (byte) 0xED;
    }

}

package com.qiji.cps.framework.redis.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.support.NullValue;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SafeRedisJsonSerializerTest {

    @SuppressWarnings("unchecked")
    private final SafeRedisJsonSerializer serializer =
            new SafeRedisJsonSerializer((RedisSerializer<Object>) RedisSerializer.json());

    @Test
    @DisplayName("正常对象序列化与反序列化测试")
    void serializeAndDeserialize_success() {
        String testData = "hello-cps";
        byte[] bytes = serializer.serialize(testData);
        assertNotNull(bytes);

        Object result = serializer.deserialize(bytes);
        assertEquals(testData, result);
    }

    @Test
    @DisplayName("空字节数组反序列化应返回 null")
    void deserialize_nullAndEmpty() {
        assertNull(serializer.deserialize(null));
        assertNull(serializer.deserialize(new byte[0]));
    }

    @Test
    @DisplayName("遇到 JDK 原生序列化魔数 0xAC 0xED（如历史 NullValue 标记）应安全返回 null 而不抛出异常")
    void deserialize_jdkSerializedStreamMagic_returnsNullWithoutException() {
        // 构造以 0xAC 0xED 开头的 Java 原生序列化字节数组（正是导致 Unexpected character '¬' (code 172) 的数据）
        byte[] jdkSerializedBytes = new byte[]{(byte) 0xAC, (byte) 0xED, 0x00, 0x05, 0x73, 0x72};
        assertDoesNotThrow(() -> {
            Object result = serializer.deserialize(jdkSerializedBytes);
            assertNull(result, "遇到 JDK 序列化数据时应降级为 null（视为缓存未命中）");
        });
    }

    @Test
    @DisplayName("遇到 Spring Cache BINARY_NULL_VALUE 时应安全返回 null")
    void deserialize_nullValueBytes_returnsNullWithoutException() {
        byte[] nullValueBytes = RedisSerializer.java().serialize(NullValue.INSTANCE);
        assertNotNull(nullValueBytes);
        assertTrue(nullValueBytes.length >= 2);
        assertEquals((byte) 0xAC, nullValueBytes[0]);
        assertEquals((byte) 0xED, nullValueBytes[1]);

        assertDoesNotThrow(() -> {
            Object result = serializer.deserialize(nullValueBytes);
            assertNull(result, "遇到 NullValue 序列化标记时应安全降级返回 null");
        });
    }

    @Test
    @DisplayName("遇到损坏的非 JSON 数据时应容错捕获并返回 null，不打断业务")
    void deserialize_corruptedBytes_returnsNullWithoutException() {
        byte[] corruptBytes = "{invalid json content !!!".getBytes(StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> {
            Object result = serializer.deserialize(corruptBytes);
            assertNull(result, "损坏的缓存字节应自动降级为 null");
        });
    }

    @Test
    @DisplayName("与 RedisCache 结合：当 disableCachingNullValues 生效且缓存中存在历史 BINARY_NULL_VALUE 时，get 应优雅返回 null（未命中）而非抛出反序列化异常")
    void redisCache_withDisableNullAndJdkMagic_returnsNullWithoutException() {
        RedisCacheWriter writer = mock(RedisCacheWriter.class);
        byte[] nullValueBytes = RedisSerializer.java().serialize(NullValue.INSTANCE);
        when(writer.get(any(), any())).thenReturn(nullValueBytes);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();

        RedisCache cache = new RedisCache("testCache", writer, config) {};
        assertDoesNotThrow(() -> {
            Cache.ValueWrapper wrapper = cache.get("testKey");
            assertNull(wrapper, "遇到历史 NullValue 标记时，cache.get 应返回 null 表示未命中回源");
        });
    }

}

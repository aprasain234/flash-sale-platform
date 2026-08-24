package com.flashsale.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Atomic "reserve" script: checks remaining inventory count, decrements it,
     * and sets a per-seat hold key with a TTL — all in one Lua execution so no
     * other client can interleave between the check and the decrement.
     * See resources/scripts/reserve_seat.lua for the script body.
     */
    @Bean
    public DefaultRedisScript<Long> reserveSeatScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/reserve_seat.lua"));
        script.setResultType(Long.class);
        return script;
    }

    /**
     * Atomic "release" script: deletes the per-seat hold key and increments the
     * remaining-inventory counter back up. Used on explicit cancel and can also
     * be invoked from a keyspace-notification listener when a hold expires.
     */
    @Bean
    public DefaultRedisScript<Long> releaseSeatScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/release_seat.lua"));
        script.setResultType(Long.class);
        return script;
    }
}

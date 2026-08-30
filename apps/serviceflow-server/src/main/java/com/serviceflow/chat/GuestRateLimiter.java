package com.serviceflow.chat;

import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import com.serviceflow.config.ServiceFlowProperties;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GuestRateLimiter {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('PEXPIRE',KEYS[1],ARGV[1]) end; return n",
            Long.class);
    private final StringRedisTemplate redis;
    private final ServiceFlowProperties properties;

    public GuestRateLimiter(StringRedisTemplate redis, ServiceFlowProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public void check(String subject, String ip) {
        long ttl = properties.guestRateLimit().window().toMillis();
        checkKey("guest:limit:subject:" + subject, ttl);
        checkKey("guest:limit:ip:" + ip, ttl);
    }

    private void checkKey(String key, long ttl) {
        Long count = redis.execute(SCRIPT, List.of(key), Long.toString(ttl));
        if (count != null && count > properties.guestRateLimit().maxRequests())
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "访客咨询过于频繁，请稍后再试");
    }
}

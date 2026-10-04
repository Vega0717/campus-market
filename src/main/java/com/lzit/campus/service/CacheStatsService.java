package com.lzit.campus.service;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * 缓存统计服务：记录命中/未命中次数，用于演示加缓存的效果。
 *
 * 实现很朴素：进程内的计数器。Spring Boot 的 Cache 本身不直接暴露命中率，
 * 要专业指标得上 Micrometer + Actuator。
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(RedisTemplate.class)
public class CacheStatsService {

    private final LongAdder hitCount = new LongAdder();
    private final LongAdder missCount = new LongAdder();
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager cacheManager;

    public CacheStatsService(RedisTemplate<String, Object> redisTemplate, CacheManager cacheManager) {
        this.redisTemplate = redisTemplate;
        this.cacheManager = cacheManager;
    }

    public void recordHit() {
        hitCount.increment();
    }

    public void recordMiss() {
        missCount.increment();
    }

    /** 汇总当前缓存状态 */
    public Map<String, Object> stats() {
        long hit = hitCount.sum();
        long miss = missCount.sum();
        long total = hit + miss;

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hitCount", hit);
        map.put("missCount", miss);
        map.put("hitRate", total == 0 ? "0%" : String.format("%.1f%%", hit * 100.0 / total));
        map.put("itemKeys", countKeys("campus:item:*"));
        map.put("itemPageKeys", countKeys("campus:itemPage:*"));

        Cache itemCache = cacheManager.getCache("item");
        map.put("cacheManager", cacheManager.getClass().getSimpleName());
        map.put("itemCacheNative", itemCache != null ? itemCache.getNativeCache().getClass().getSimpleName() : "null");
        return map;
    }

    public void reset() {
        hitCount.reset();
        missCount.reset();
    }

    private long countKeys(String pattern) {
        try {
            var keys = redisTemplate.keys(pattern);
            return keys == null ? 0 : keys.size();
        } catch (Exception e) {
            return -1;
        }
    }
}

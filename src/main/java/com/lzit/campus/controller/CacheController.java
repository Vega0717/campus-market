package com.lzit.campus.controller;

import com.lzit.campus.common.Result;
import com.lzit.campus.service.CacheStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 缓存运维接口：查看缓存的访问统计、手动清空统计。
 * 加这个接口纯粹是为了方便演示「加缓存前后差多少」，面试时可以现场展示。
 *
 * 只在 Redis 启用时才注册（Redis 没配的话这个接口不存在，访问返回 404）。
 */
@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
@ConditionalOnBean(RedisTemplate.class)
public class CacheController {

    private final CacheStatsService cacheStatsService;

    /** 查看缓存访问统计（命中次数、命中率、当前 key 数量） */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(cacheStatsService.stats());
    }

    /** 重置统计 */
    @PostMapping("/stats/reset")
    public Result<Void> reset() {
        cacheStatsService.reset();
        return Result.ok();
    }
}

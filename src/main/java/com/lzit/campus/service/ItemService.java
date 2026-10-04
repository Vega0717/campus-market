package com.lzit.campus.service;

import com.lzit.campus.common.PageResult;
import com.lzit.campus.config.RedisConfig;
import com.lzit.campus.entity.Item;
import com.lzit.campus.exception.BizException;
import com.lzit.campus.mapper.ItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 商品业务逻辑层（带 Redis 缓存）
 *
 * 缓存策略：
 * - 详情查库前先查缓存，命中直接返回；
 * - 列表按「关键词 + 状态 + 页码 + 每页条数」拼 key 缓存；
 * - 任何写操作都要清缓存，否则用户看到过期数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemService {

    public static final int STATUS_ON_SALE = 0;
    public static final int STATUS_SOLD = 1;
    public static final int STATUS_OFF = 2;

    /** Redis 没启用时这些 Bean 不存在，用 ObjectProvider 优雅取用 */
    private final ItemMapper itemMapper;
    private final org.springframework.beans.factory.ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;
    private final org.springframework.beans.factory.ObjectProvider<CacheStatsService> cacheStatsProvider;

    private RedisTemplate<String, Object> redis() {
        return redisTemplateProvider.getIfAvailable();
    }

    private static final String KEY_PREFIX = "campus:";
    private static final String ITEM_KEY_PREFIX = KEY_PREFIX + RedisConfig.CacheNames.ITEM + ":";
    private static final String PAGE_KEY_PREFIX = KEY_PREFIX + RedisConfig.CacheNames.ITEM_PAGE + ":";

    /** 分页查询：先查 Redis，未命中再查 MySQL 并回写。key 形如 campus:itemPage:教材:0:1:10 */
    public PageResult<Item> page(String keyword, Integer status, long page, long size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 10;
        }

        String cacheKey = PAGE_KEY_PREFIX
                + (keyword == null ? "all" : keyword) + ":"
                + (status == null ? "all" : status) + ":"
                + page + ":" + size;

        // 1. 查缓存
        Object cached = getSafely(cacheKey);
        if (cached instanceof PageResult<?> pageResult) {
            CacheStatsService stats = cacheStatsProvider.getIfAvailable();
            if (stats != null) {
                stats.recordHit();
            }
            log.debug("[缓存命中] key={}", cacheKey);
            @SuppressWarnings("unchecked")
            PageResult<Item> hit = (PageResult<Item>) pageResult;
            return hit;
        }

        // 2. 未命中，查数据库
        CacheStatsService stats = cacheStatsProvider.getIfAvailable();
        if (stats != null) {
            stats.recordMiss();
        }
        log.debug("[缓存未命中] key={} 回查数据库", cacheKey);
        long offset = (page - 1) * size;
        List<Item> records = itemMapper.selectPage(keyword, status, offset, size);
        long total = itemMapper.countByCondition(keyword, status);
        PageResult<Item> result = PageResult.of(total, page, size, records);

        // 3. 回写缓存（2 分钟 TTL）
        putSafely(cacheKey, result);
        return result;
    }

    /** 详情：@Cacheable 走 Spring 缓存抽象，sync=true 防缓存击穿 */
    @Cacheable(cacheNames = RedisConfig.CacheNames.ITEM, key = "#id", sync = true)
    public Item detail(Long id) {
        log.debug("[缓存未命中] 查库 item id={}", id);
        Item item = itemMapper.selectById(id);
        if (item == null) {
            throw new BizException(404, "商品不存在或已删除");
        }
        return item;
    }

    public Item publish(Item item) {
        if (item.getStatus() == null) {
            item.setStatus(STATUS_ON_SALE);
        }
        itemMapper.insert(item);
        evictPageCache();
        return item;
    }

    public Item update(Long id, Item item) {
        Item exist = itemMapper.selectById(id);
        if (exist == null) {
            throw new BizException(404, "商品不存在或已删除");
        }
        item.setId(exist.getId());
        if (item.getStatus() == null) {
            item.setStatus(exist.getStatus());
        }
        itemMapper.update(item);
        evictItemAndPageCache(id);
        return itemMapper.selectById(id);
    }

    public void markSold(Long id) {
        Item exist = itemMapper.selectById(id);
        if (exist == null) {
            throw new BizException(404, "商品不存在或已删除");
        }
        if (exist.getStatus() == STATUS_SOLD) {
            throw new BizException("该商品已经是已售出状态");
        }
        itemMapper.updateStatus(id, STATUS_SOLD);
        evictItemAndPageCache(id);
    }

    public void delete(Long id) {
        Item exist = itemMapper.selectById(id);
        if (exist == null) {
            throw new BizException(404, "商品不存在或已删除");
        }
        itemMapper.deleteById(id);
        evictItemAndPageCache(id);
    }

    // ==================== 缓存操作封装 ====================

    /** 读缓存。Redis 挂了不能让接口跟着挂，吞异常当未命中 */
    private Object getSafely(String key) {
        RedisTemplate<String, Object> redisTemplate = redis();
        if (redisTemplate == null) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("读取 Redis 失败，降级为直接查库。key={}, 原因={}", key, e.getMessage());
            return null;
        }
    }

    private void putSafely(String key, Object value) {
        RedisTemplate<String, Object> redisTemplate = redis();
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value, java.time.Duration.ofMinutes(2));
        } catch (Exception e) {
            log.warn("写入 Redis 失败，本次不做缓存。key={}, 原因={}", key, e.getMessage());
        }
    }

    private void evictPageCache() {
        deleteByPrefix(PAGE_KEY_PREFIX);
    }

    private void evictItemAndPageCache(Long id) {
        RedisTemplate<String, Object> redisTemplate = redis();
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(ITEM_KEY_PREFIX + id);
        } catch (Exception e) {
            log.warn("删除详情缓存失败 id={}, 原因={}", id, e.getMessage());
        }
        evictPageCache();
    }

    /** 按前缀批量删除。生产环境数据量大应该用 SCAN，本项目数据量小用 KEYS 便于理解 */
    private void deleteByPrefix(String prefix) {
        RedisTemplate<String, Object> redisTemplate = redis();
        if (redisTemplate == null) {
            return;
        }
        try {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("[缓存失效] 删除 {} 个 key，前缀={}", keys.size(), prefix);
            }
        } catch (Exception e) {
            log.warn("批量删除缓存失败，前缀={}, 原因={}", prefix, e.getMessage());
        }
    }
}

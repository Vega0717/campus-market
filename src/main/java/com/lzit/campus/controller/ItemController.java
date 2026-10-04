package com.lzit.campus.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lzit.campus.common.PageResult;
import com.lzit.campus.common.Result;
import com.lzit.campus.entity.Item;
import com.lzit.campus.service.ItemService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 商品接口（RESTful 风格），统一前缀 /api/items
 */
@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    /** 分页列表：GET /api/items?page=1&size=10&keyword=教材&status=0 */
    @GetMapping
    public Result<PageResult<Item>> page(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(itemService.page(keyword, status, page, size));
    }

    /** 详情：GET /api/items/1 */
    @GetMapping("/{id}")
    public Result<Item> detail(@PathVariable Long id) {
        return Result.ok(itemService.detail(id));
    }

    /** 发布：POST /api/items */
    @PostMapping
    public Result<Item> publish(@Valid @RequestBody Item item) {
        return Result.ok(itemService.publish(item));
    }

    /** 修改：PUT /api/items/1 */
    @PutMapping("/{id}")
    public Result<Item> update(@PathVariable Long id, @Valid @RequestBody Item item) {
        return Result.ok(itemService.update(id, item));
    }

    /** 标记已售出：POST /api/items/1/sold */
    @PostMapping("/{id}/sold")
    public Result<Void> markSold(@PathVariable Long id) {
        itemService.markSold(id);
        return Result.ok();
    }

    /** 删除：DELETE /api/items/1 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        itemService.delete(id);
        return Result.ok();
    }
}

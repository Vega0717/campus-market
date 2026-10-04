package com.lzit.campus.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.lzit.campus.entity.Item;

/**
 * 商品数据访问层（真正的 SQL 写在 resources/mapper/ItemMapper.xml）
 */
@Mapper
public interface ItemMapper {

    /** 分页 + 关键词 + 状态查询 */
    List<Item> selectPage(@Param("keyword") String keyword,
            @Param("status") Integer status,
            @Param("offset") long offset,
            @Param("size") long size);

    /** 统计总数（条件和 selectPage 一致） */
    long countByCondition(@Param("keyword") String keyword,
            @Param("status") Integer status);

    Item selectById(@Param("id") Long id);

    int insert(Item item);

    int update(Item item);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int deleteById(@Param("id") Long id);
}

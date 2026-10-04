package com.lzit.campus.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class Item {

    private Long id;

    @NotBlank(message = "标题不能为空")
    private String title;

    private String description;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    private BigDecimal price;

    private String category;

    @NotBlank(message = "卖家不能为空")
    private String seller;

    private String contact;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

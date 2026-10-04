package com.lzit.campus.exception;

import lombok.Getter;

/**
 * 业务异常：可预期的错误（数据不存在、状态不合法等）
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = 400;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}

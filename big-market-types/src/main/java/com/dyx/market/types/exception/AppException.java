package com.dyx.market.types.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务运行时异常：携带 {@code code} 与 {@code info}，对应 {@link com.dyx.market.types.enums.ResponseCode}。
 *
 * <p>由应用层、领域服务抛出，经 Controller / Dubbo 统一封装为 {@link com.dyx.market.types.common.Response}。</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AppException extends RuntimeException {

    private static final long serialVersionUID = 5317680961212299217L;

    /** 异常码 */
    private String code;

    /** 异常信息 */
    private String info;

    public AppException(String code) {
        this.code = code;
    }

    public AppException(String code, Throwable cause) {
        this.code = code;
        super.initCause(cause);
    }

    public AppException(String code, String message) {
        this.code = code;
        this.info = message;
        super.initCause(new Throwable(message));
    }

    public AppException(String code, String message, Throwable cause) {
        this.code = code;
        this.info = message;
        super.initCause(cause);
    }

    @Override
    public String toString() {
        return "com.dyx.market.types.exception.AppException{" +
                "code='" + code + '\'' +
                ", info='" + info + '\'' +
                '}';
    }

}

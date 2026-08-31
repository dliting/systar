package com.systar.common.api;

import lombok.Data;

/**
 * Unified REST API response wrapper.
 * <p>
 * All controller endpoints return {@code Result<T>} to provide a consistent
 * JSON structure: {@code {"code": 0, "message": "...", "data": ...}}.
 *
 * @param <T> the type of the payload
 */
@Data
public class Result<T> {

    public static final int CODE_SUCCESS = 0;
    public static final int CODE_ERROR = 1;
    public static final int CODE_BAD_REQUEST = 400;
    public static final int CODE_UNAUTHORIZED = 401;
    public static final int CODE_FORBIDDEN = 403;
    public static final int CODE_NOT_FOUND = 404;
    public static final int CODE_CONFLICT = 409;
    public static final int CODE_INTERNAL_ERROR = 500;

    /** Status code: 0 = success, non-zero = error. */
    private int code;

    /** Human-readable status message. */
    private String message;

    /** Response payload. */
    private T data;

    private Result() {
    }

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * Creates a success result with the given data payload.
     *
     * @param data the response payload
     * @param <T>  the payload type
     * @return success Result
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(CODE_SUCCESS, "success", data);
    }

    /**
     * Creates a success result with no payload.
     *
     * @param <T> the payload type
     * @return success Result with null data
     */
    public static <T> Result<T> success() {
        return new Result<>(CODE_SUCCESS, "success", null);
    }

    /**
     * Creates an error result with the given message.
     *
     * @param message error description
     * @param <T>     the payload type
     * @return error Result
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(CODE_ERROR, message, null);
    }

    /**
     * Creates an error result with the given code and message.
     *
     * @param code    error code
     * @param message error description
     * @param <T>     the payload type
     * @return error Result
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}

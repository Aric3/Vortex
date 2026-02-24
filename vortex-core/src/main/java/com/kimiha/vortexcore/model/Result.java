package com.kimiha.vortexcore.model;

import lombok.Data;

@Data
public class Result {
    private boolean success;
    private int code;
    private String message;
    private Object data;
    private long timestamp;

    public static Result success(Object data, String message) {
        Result r = new Result();
        r.success = true;
        r.code = ResultCode.SUCCESS.getCode();
        r.message = message;
        r.data = data;
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static Result success(Object data) {
        return success(data, "OK");
    }

    public static Result fail(ResultCode code, String message) {
        Result r = new Result();
        r.success = false;
        r.code = code.getCode();
        r.message = message;
        r.data = null;
        r.timestamp = System.currentTimeMillis();
        return r;
    }
}

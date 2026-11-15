package com.example.demo.exception;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    public ApiException(int status, String code, String message) { super(message); this.status=status; this.code=code; }
    public int status() { return status; }
    public String code() { return code; }
    public static ApiException forbidden() { return new ApiException(403,"FORBIDDEN","You do not have permission for this action."); }
    public static ApiException missing() { return new ApiException(404,"NOT_FOUND","The requested item is unavailable."); }
    public static ApiException invalid(String message) { return new ApiException(400,"VALIDATION",message); }
    public static ApiException conflict(String message) { return new ApiException(409,"CONFLICT",message); }
}

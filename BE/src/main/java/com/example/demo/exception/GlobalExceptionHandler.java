package com.example.demo.exception;

import java.util.Map;
import com.example.demo.controller.ApiController;
import com.example.demo.controller.DemoJobsController;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes={ApiController.class,DemoJobsController.class})
public class GlobalExceptionHandler {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private ResponseEntity<?> error(int status,String code,String message) { return ResponseEntity.status(status).body(Map.of("success",false,"error",Map.of("code",code,"message",message))); }
    @ExceptionHandler(ApiException.class) public ResponseEntity<?> known(ApiException e) { return error(e.status(),e.code(),e.getMessage()); }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> invalid(Exception e) { return error(400,"VALIDATION","Check the submitted values, IDs and dates."); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> constraint(Exception e) { return error(409,"CONFLICT","This change conflicts with database constraints. Refresh and check the current state."); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception e) {
        LOG.error("API request failed",e);
        return error(500,"SERVER_ERROR","The server could not complete this request. Please try again.");
    }
}

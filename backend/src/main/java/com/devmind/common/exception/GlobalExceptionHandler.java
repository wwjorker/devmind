package com.devmind.common.exception;

import com.devmind.common.api.Result;
import com.devmind.common.api.ResultCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBizException(BizException ex) {
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleValidationException(Exception ex) {
        String message;
        if (ex instanceof MethodArgumentNotValidException validException) {
            message = validException.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + " " + error.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        } else {
            BindException bindException = (BindException) ex;
            message = bindException.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + " " + error.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        }
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException ex) {
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleAccessDeniedException() {
        return Result.fail(ResultCode.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleException(Exception ex) {
        return Result.fail(ResultCode.INTERNAL_ERROR.getCode(), ex.getMessage());
    }
}

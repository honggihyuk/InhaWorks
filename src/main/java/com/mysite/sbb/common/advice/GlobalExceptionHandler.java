package com.mysite.sbb.common.advice;

import com.mysite.sbb.common.exception.BusinessException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

// 업무 오류 / 없는 데이터 → 안내 화면 (message.html)
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({BusinessException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String business(RuntimeException e, Model model) {
        log.info("=======> 업무 오류 : {}", e.getMessage());
        model.addAttribute("message", e.getMessage());
        return "message";
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(EntityNotFoundException e, Model model) {
        model.addAttribute("message", e.getMessage() == null ? "찾을 수 없어요." : e.getMessage());
        return "message";
    }
}

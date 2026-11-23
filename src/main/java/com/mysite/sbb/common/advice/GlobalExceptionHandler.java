package com.mysite.sbb.common.advice;

import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.common.exception.NoAccessException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.ui.Model;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

// [방어] 전역 예외 처리 : 어떤 오류가 나도 사용자에게는 안전한 안내 화면(message.html)만 보여 준다
//  - 스택 트레이스·SQL·클래스 이름은 화면에 내보내지 않는다 (로그에만)
//  - 오류마다 짧은 요청 번호(requestId)를 만들어 화면과 로그에 함께 남긴다 → 문의가 오면 로그에서 바로 찾는다
//  - 상태 코드 : 업무 오류 400, 없는 데이터 404, 동시 수정·중복 409, 용량 초과 413, 그 밖 500
//  - 보안 예외(권한·로그인)는 여기서 삼키지 않고 Spring Security 로 넘긴다
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    public static final String REQUEST_ID = "requestId";

    // 볼 권한이 없는 링크 : 내용은 보여 주지 않고 안내 화면만 (ISS-21)
    @ExceptionHandler(NoAccessException.class)
    public String noAccess(NoAccessException e, HttpServletResponse res, Model model) {
        log.info("=======> 접근 거부 : {}", e.getMessage());
        return view(res, model, HttpStatus.FORBIDDEN, e.getMessage(), null);
    }

    // 업무 규칙 위반 (사용자가 고칠 수 있는 오류) : 메시지를 그대로 보여 준다
    @ExceptionHandler({BusinessException.class, IllegalArgumentException.class})
    public String business(RuntimeException e, HttpServletResponse res, Model model) {
        log.info("=======> 업무 오류 : {}", e.getMessage());
        return view(res, model, HttpStatus.BAD_REQUEST, e.getMessage(), null);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public String notFound(EntityNotFoundException e, HttpServletResponse res, Model model) {
        return view(res, model, HttpStatus.NOT_FOUND, e.getMessage() == null ? "찾을 수 없어요." : e.getMessage(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public String noResource(NoResourceFoundException e, HttpServletResponse res, Model model) {
        return view(res, model, HttpStatus.NOT_FOUND, "없는 화면이에요. 주소를 확인해 주세요.", null);
    }

    // 낙관적 락 충돌 (@Version) : 재시도 후에도 충돌하면 여기로 온다. 변경은 롤백되어 있다
    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public String conflict(Exception e, HttpServletRequest req, HttpServletResponse res, Model model) {
        String id = newId();
        log.warn("=======> [{}] 동시 수정 충돌 {} {} : {}", id, req.getMethod(), req.getRequestURI(), e.toString());
        return view(res, model, HttpStatus.CONFLICT, "다른 사람이 먼저 수정했어요. 화면을 새로 고친 뒤 다시 시도해 주세요. (저장되지 않았어요)", id);
    }

    // 중복 · 제약 위반 (예: 두 번 누른 제출) : 사용자에게 DB 메시지는 보여 주지 않는다
    @ExceptionHandler(DataIntegrityViolationException.class)
    public String integrity(DataIntegrityViolationException e, HttpServletRequest req, HttpServletResponse res, Model model) {
        String id = newId();
        log.warn("=======> [{}] 데이터 제약 위반 {} {} : {}", id, req.getMethod(), req.getRequestURI(), e.getMostSpecificCause().toString());
        return view(res, model, HttpStatus.CONFLICT, "이미 처리됐거나 같은 내용이 있어요. 새로 고친 뒤 확인해 주세요. (저장되지 않았어요)", id);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(MaxUploadSizeExceededException e, HttpServletResponse res, Model model) {
        return view(res, model, HttpStatus.PAYLOAD_TOO_LARGE, "파일이 너무 커요. 파일 하나는 10MB(채팅 첨부는 20MB), 한 번에 60MB(채팅은 10개)까지 올릴 수 있어요.", null);
    }

    @ExceptionHandler(MultipartException.class)
    public String multipart(MultipartException e, HttpServletResponse res, Model model) {
        log.info("=======> 업로드 오류 : {}", e.toString());
        return view(res, model, HttpStatus.BAD_REQUEST, "파일을 올리지 못했어요. 다시 시도해 주세요.", null);
    }

    // 잘못된 주소·값 (예: /board/abc, 빠진 필수 값, GET 으로 보낸 저장 요청)
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            HttpRequestMethodNotSupportedException.class})
    public String badRequest(Exception e, HttpServletResponse res, Model model) {
        log.info("=======> 잘못된 요청 : {}", e.getMessage());
        return view(res, model, HttpStatus.BAD_REQUEST, "요청이 올바르지 않아요. 화면에서 다시 시도해 주세요.", null);
    }

    // 그 밖의 예상하지 못한 오류 : 로그에 요청 번호와 함께 전체 스택을 남기고, 화면에는 번호만
    @ExceptionHandler(Exception.class)
    public String unexpected(Exception e, HttpServletRequest req, HttpServletResponse res, Model model) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e;    // 권한·로그인은 Spring Security 가 처리 (로그인 화면 / 거부 화면)
        }
        String id = newId();
        log.error("=======> [{}] 예상하지 못한 오류 {} {}", id, req.getMethod(), req.getRequestURI(), e);
        return view(res, model, HttpStatus.INTERNAL_SERVER_ERROR,
                "잠시 문제가 생겨 처리하지 못했어요. 저장 중이었다면 반영되지 않았어요. 잠시 뒤 다시 시도해 주세요.", id);
    }

    private static String view(HttpServletResponse res, Model model, HttpStatus status, String message, String id) {
        res.setStatus(status.value());
        model.addAttribute("message", message);
        model.addAttribute("status", status.value());
        if (id != null) model.addAttribute(REQUEST_ID, id);
        return "message";
    }

    static String newId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

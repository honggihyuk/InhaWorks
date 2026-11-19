package com.mysite.sbb.common.dto;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// 제출 화면에서 온 항목 하나의 답 (할 일 / 보고서 공통) (글자 값들 / 사진 파일 / 서명 data URL)
public record AnswerInput(List<String> values, MultipartFile file, String signature) {

    public String joinedValue() {
        if (values == null) return null;
        List<String> v = values.stream().filter(s -> s != null && !s.isBlank()).map(String::strip).toList();
        return v.isEmpty() ? null : String.join("|", v);
    }

    public boolean hasFile() {
        return file != null && !file.isEmpty();
    }

    public boolean hasSignature() {
        return signature != null && signature.startsWith("data:image/png;base64,");
    }
}

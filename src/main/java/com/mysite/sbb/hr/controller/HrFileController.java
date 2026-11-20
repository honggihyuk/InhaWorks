package com.mysite.sbb.hr.controller;

import com.mysite.sbb.hr.entity.HrDocument;
import com.mysite.sbb.hr.service.HrService;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.Principal;

// 인사 서류 파일 내려받기 : 공개 주소(/uploads/**)가 아니라 여기서 열람 권한을 확인한 뒤 내려준다
//  inline=true 이면 브라우저에서 바로 보기 (이미지·PDF 미리보기), 아니면 내려받기. 캐시하지 않는다
@Controller
@RequiredArgsConstructor
public class HrFileController {

    private final HrService hrService;
    private final MemberService memberService;

    @GetMapping("/hr/docs/{id}/file")
    public ResponseEntity<FileSystemResource> file(@PathVariable("id") Long id,
                                                   @RequestParam(value = "inline", defaultValue = "false") boolean inline,
                                                   Principal principal) {
        Member viewer = memberService.getMember(principal.getName());
        Path path = hrService.openFile(id, viewer);
        HrDocument doc = hrService.getDocument(id);
        String name = doc.getFile().getOriginalName() == null ? path.getFileName().toString() : doc.getFile().getOriginalName();
        MediaType type = MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM);
        ContentDisposition cd = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(name, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
    }
}

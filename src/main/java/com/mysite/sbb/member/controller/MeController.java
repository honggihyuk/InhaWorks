package com.mysite.sbb.member.controller;

import com.mysite.sbb.common.exception.BusinessException;
import com.mysite.sbb.common.file.FileStore;
import com.mysite.sbb.hr.service.HrService;
import com.mysite.sbb.member.entity.Member;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import java.time.LocalDate;
import com.mysite.sbb.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

// 내 정보 (프로필 사진 바꾸기 / 지우기, 내 인사 서류 제출, 로그아웃)
@Controller
@RequiredArgsConstructor
public class MeController {

    private final MemberService memberService;
    private final FileStore fileStore;
    private final HrService hrService;

    @GetMapping("/me")
    public String me(Principal principal, Model model) {
        // 회원 정보는 GlobalModelAdvice 의 loginMember 사용. 인사 서류는 내가 볼 수 있는 종류만
        Member me = memberService.getMember(principal.getName());
        model.addAttribute("myDocs", hrService.getDocRows(me, me));
        return "member/me";
    }

    // 내 서류 제출 / 교체 (직원 제출 가능 서류만, 권한은 HrService 가 다시 확인)
    @PostMapping("/me/docs")
    public String uploadDoc(@RequestParam("typeId") Long typeId,
                            @RequestParam(value = "file", required = false) MultipartFile file,
                            @RequestParam(value = "expiresAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresAt,
                            Principal principal, RedirectAttributes ra) {
        Member me = memberService.getMember(principal.getName());
        try {
            var doc = hrService.upload(me.getId(), typeId, file, expiresAt, me);
            ra.addFlashAttribute("attendanceMessage", doc.getType().getName() + " 서류를 냈어요.");
        } catch (BusinessException | IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/me#docs";
    }

    @PostMapping("/me/docs/{id}/delete")
    public String deleteDoc(@PathVariable("id") Long id, Principal principal, RedirectAttributes ra) {
        Member me = memberService.getMember(principal.getName());
        if (!hrService.getDocument(id).getMember().getId().equals(me.getId())) throw new BusinessException("내 서류만 지울 수 있어요.");
        String name = hrService.deleteDocument(id, me);
        ra.addFlashAttribute("attendanceMessage", name + " 서류를 지웠어요.");
        return "redirect:/me#docs";
    }

    @PostMapping("/me/photo")
    public String changePhoto(@RequestParam("photo") MultipartFile photo,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {
        try {
            String path = fileStore.store(photo);
            if (path != null) {
                memberService.updatePhoto(principal.getName(), path);
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/me";
    }

    @PostMapping("/me/photo/delete")
    public String deletePhoto(Principal principal) {
        memberService.updatePhoto(principal.getName(), null);
        return "redirect:/me";
    }
}

package com.mysite.sbb.admin.controller;

import com.mysite.sbb.admin.dto.AdminMemberDto;
import com.mysite.sbb.admin.service.AdminMemberService;
import com.mysite.sbb.member.constant.MemberRole;
import com.mysite.sbb.member.entity.Member;
import com.mysite.sbb.member.service.MemberService;
import com.mysite.sbb.organization.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

// 관리자 모드 : 직원 목록 / 등록 / 수정 / 퇴사 처리, 권한 변경, 조직(팀·근무지) 관리
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminController {

    private final MemberService memberService;
    private final AdminMemberService adminMemberService;
    private final OrganizationService organizationService;

    // 관리자 로그인 화면 (POST /admin/login 은 Spring Security 가 처리)
    @GetMapping("/login")
    public String login() {
        return "admin/login";
    }

    // 직원 목록 (관리자 메인은 대시보드 : AdminDashboardController)
    @GetMapping("/members")
    public String index(Model model) {
        model.addAttribute("memberList", memberService.getMemberList());
        model.addAttribute("roles", MemberRole.values());
        return "admin/index";
    }

    // 직원 권한(직원/매니저/관리자) 변경 (목록에서 바로)
    @PostMapping("/member/{id}")
    public String updateRole(@PathVariable("id") Long id,
                             @RequestParam("role") MemberRole role,
                             Principal principal, RedirectAttributes ra) {
        if (isMe(id, principal) && role != MemberRole.ADMIN) {
            ra.addFlashAttribute("errorMessage", "내 계정의 관리자 권한은 뺄 수 없어요.");
            return "redirect:/admin/members";
        }
        adminMemberService.updateRole(id, role);
        return "redirect:/admin/members";
    }

    // ---------- 직원 등록 ----------

    @GetMapping("/member/new")
    public String createForm(AdminMemberDto adminMemberDto, Model model) {
        return form(model, null);
    }

    @PostMapping("/member/new")
    public String create(@Valid AdminMemberDto adminMemberDto, BindingResult bindingResult,
                         Model model, RedirectAttributes ra) {
        if (adminMemberDto.getPassword() == null || adminMemberDto.getPassword().length() < 4) {
            bindingResult.rejectValue("password", "required", "비밀번호를 4자 이상 입력해주세요.");
        }
        checkDuplicate(adminMemberDto, null, bindingResult);
        if (bindingResult.hasErrors()) {
            return form(model, null);
        }
        Member member = adminMemberService.create(adminMemberDto);
        log.info("=======> 관리자 직원 등록 : {}", member.getUsername());
        ra.addFlashAttribute("adminMessage", member.getDisplayName() + " 님을 등록했어요.");
        return "redirect:/admin/members";
    }

    // ---------- 직원 수정 ----------

    @GetMapping("/member/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        model.addAttribute("adminMemberDto", AdminMemberDto.of(adminMemberService.getMember(id)));
        return form(model, id);
    }

    @PostMapping("/member/{id}/edit")
    public String edit(@PathVariable("id") Long id, @Valid AdminMemberDto adminMemberDto,
                       BindingResult bindingResult, Principal principal, Model model, RedirectAttributes ra) {
        String password = adminMemberDto.getPassword();
        if (password != null && !password.isBlank() && password.length() < 4) {
            bindingResult.rejectValue("password", "short", "비밀번호는 4자 이상 입력해주세요.");
        }
        checkDuplicate(adminMemberDto, id, bindingResult);
        if (isMe(id, principal)) {
            // 로그인한 계정의 아이디·권한이 바뀌면 바로 로그아웃되므로 막는다
            if (!adminMemberDto.getUsername().equals(principal.getName())) {
                bindingResult.rejectValue("username", "self", "로그인한 내 계정의 아이디는 바꿀 수 없어요.");
            }
            if (adminMemberDto.getRole() != MemberRole.ADMIN) {
                bindingResult.rejectValue("role", "self", "내 계정의 관리자 권한은 뺄 수 없어요.");
            }
            adminMemberDto.setActive(true);
        }
        if (bindingResult.hasErrors()) {
            return form(model, id);
        }
        adminMemberService.update(id, adminMemberDto);
        ra.addFlashAttribute("adminMessage", adminMemberDto.getName() + " 님 정보를 고쳤어요.");
        return "redirect:/admin/members";
    }

    // ---------- 퇴사 처리 / 복직 ----------

    @PostMapping("/member/{id}/active")
    public String toggleActive(@PathVariable("id") Long id, Principal principal, RedirectAttributes ra) {
        if (isMe(id, principal)) {
            ra.addFlashAttribute("errorMessage", "로그인한 내 계정은 퇴사 처리할 수 없어요.");
            return "redirect:/admin/members";
        }
        adminMemberService.toggleActive(id);
        Member member = adminMemberService.getMember(id);
        ra.addFlashAttribute("adminMessage", member.getDisplayName() + (member.isActive() ? " 님을 복직 처리했어요." : " 님을 퇴사 처리했어요."));
        return "redirect:/admin/members";
    }

    // ---------- 조직 : 팀 / 근무지 ----------

    @GetMapping("/org")
    public String org(Model model) {
        model.addAttribute("teams", organizationService.getTeams());
        model.addAttribute("workplaces", organizationService.getWorkplaces());
        return "admin/org";
    }

    @PostMapping("/org/team")
    public String createTeam(@RequestParam("name") String name,
                             @RequestParam(value = "description", required = false) String description) {
        organizationService.createTeam(name, description);
        return "redirect:/admin/org";
    }

    @PostMapping("/org/team/{id}/delete")
    public String deleteTeam(@PathVariable("id") Long id) {
        organizationService.deleteTeam(id);
        return "redirect:/admin/org";
    }

    @PostMapping("/org/workplace")
    public String createWorkplace(@RequestParam("name") String name,
                                  @RequestParam(value = "address", required = false) String address) {
        organizationService.createWorkplace(name, address);
        return "redirect:/admin/org";
    }

    @PostMapping("/org/workplace/{id}/delete")
    public String deleteWorkplace(@PathVariable("id") Long id) {
        organizationService.deleteWorkplace(id);
        return "redirect:/admin/org";
    }

    private String form(Model model, Long id) {
        model.addAttribute("memberId", id);          // null 이면 등록, 있으면 수정
        model.addAttribute("roles", MemberRole.values());
        model.addAttribute("teams", organizationService.getTeams());
        model.addAttribute("workplaces", organizationService.getWorkplaces());
        return "admin/member-form";
    }

    private void checkDuplicate(AdminMemberDto dto, Long id, BindingResult bindingResult) {
        if (dto.getUsername() != null && adminMemberService.isUsernameTaken(dto.getUsername().strip(), id)) {
            bindingResult.rejectValue("username", "duplicate", "이미 사용 중인 사용자ID입니다.");
        }
        String email = MemberService.emptyToNull(dto.getEmail());
        if (email != null && adminMemberService.isEmailTaken(email, id)) {
            bindingResult.rejectValue("email", "duplicate", "이미 사용 중인 이메일입니다.");
        }
    }

    private boolean isMe(Long id, Principal principal) {
        return memberService.getMember(principal.getName()).getId().equals(id);
    }
}

package com.mysite.sbb.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

// 로그인 화면 분리 (회귀) : 직원 로그인에서 관리자 계정, 관리자 로그인에서 직원 계정은 로그인되지 않는다
//  예시 데이터 계정 : admin / admin1234, emp2 / 1234
@SpringBootTest
class LoginPageSeparationTest {

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("직원 로그인에 관리자 계정 → 로그인 안 됨, 관리자 로그인 안내(?wrongPage)")
    void adminCannotLoginOnMemberPage() throws Exception {
        mvc.perform(formLogin("/member/login").user("admin").password("admin1234"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/member/login?wrongPage"));
    }

    @Test
    @DisplayName("관리자 계정이라도 비밀번호가 틀리면 일반 오류(?error) : 계정 종류를 드러내지 않는다")
    void wrongPasswordShowsGenericError() throws Exception {
        mvc.perform(formLogin("/member/login").user("admin").password("nope"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/member/login?error"));
    }

    @Test
    @DisplayName("관리자 로그인에 직원 계정 → 로그인 안 됨, 직원 로그인 안내(?wrongPage)")
    void employeeCannotLoginOnAdminPage() throws Exception {
        mvc.perform(formLogin("/admin/login").user("emp2").password("1234"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/admin/login?wrongPage"));
    }

    @Test
    @DisplayName("제자리 로그인은 그대로 : 직원 → /, 관리자 → /admin")
    void normalLoginsStillWork() throws Exception {
        mvc.perform(formLogin("/member/login").user("emp2").password("1234"))
                .andExpect(authenticated().withUsername("emp2"))
                .andExpect(redirectedUrl("/"));
        mvc.perform(formLogin("/admin/login").user("admin").password("admin1234"))
                .andExpect(authenticated().withUsername("admin"))
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    @DisplayName("이미 로그인한 관리자가 '/' 나 직원 로그인 화면에 오면 /admin 으로")
    void loggedInAdminGoesToAdmin() throws Exception {
        mvc.perform(get("/").with(user("admin").roles("ADMIN")))
                .andExpect(redirectedUrl("/admin"));
        mvc.perform(get("/member/login").with(user("admin").roles("ADMIN")))
                .andExpect(redirectedUrl("/admin"));
    }
}

package com.mysite.sbb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

import java.util.List;

// 로그인을 두 갈래로 나눈다.
//  - 관리자 : /admin/**  → 관리자 로그인 화면(/admin/login), ADMIN 권한만 접근
//  - 직원   : 나머지     → 직원 로그인 화면(/member/login)
// SecurityFilterChain 을 2개 만들고, @Order 숫자가 작은 쪽(관리자)을 먼저 검사한다.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // [보안] 관리자 접속 허용 IP (쉼표로 여러 개). 비워 두면 제한 없음. 예) 127.0.0.1,0:0:0:0:0:0:0:1,192.168.0.
    @Value("${app.security.admin-allowed-ips:}")
    private List<String> adminAllowedIps;

    // ===== 1. 관리자용 =====
    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {

        http.securityMatcher("/admin/**");   // 이 체인은 /admin 으로 시작하는 요청만 담당

        // 허용 IP 가 아니면 로그인 화면조차 보여주지 않는다 (가장 앞에서 거른다)
        http.addFilterBefore(new AdminIpAllowFilter(adminAllowedIps), SecurityContextHolderFilter.class);

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/admin/login").permitAll()
                .anyRequest().hasRole("ADMIN"));          // ROLE_ADMIN 만 통과

        http.formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/login")
                .defaultSuccessUrl("/admin", true)
                .failureUrl("/admin/login?error")
                .permitAll());

        http.logout(logout -> logout
                .logoutUrl("/admin/logout")
                .logoutSuccessUrl("/admin/login?logout")
                .invalidateHttpSession(true));

        // 직원 계정으로 관리자 화면에 들어오면 관리자 로그인 화면으로 돌려보낸다
        http.exceptionHandling(ex -> ex.accessDeniedPage("/admin/login?denied"));

        return http.build();
    }

    // ===== 2. 직원용 =====
    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // 1) URL 별 접근 권한 (사내 시스템이라 로그인해야 모든 화면을 볼 수 있다)
        //    검수·결재 같은 세부 권한은 서비스에서 확인한다 (검수자 / 결재자 본인인지)
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/member/**").permitAll()   // 로그인 화면
                .anyRequest().authenticated());              // 나머지는 로그인 필요 (업로드 파일 /uploads/** 포함)

        // 권한이 없으면 안내 화면으로
        http.exceptionHandling(ex -> ex.accessDeniedPage("/denied"));

        // 2) 로그인 : 직접 만든 화면(/member/login) 사용
        //    POST /member/login 요청은 Spring Security 가 처리하고,
        //    사용자 조회는 MemberSecurityService(UserDetailsService) 가 담당
        http.formLogin(form -> form
                .loginPage("/member/login")
                .defaultSuccessUrl("/")
                .failureUrl("/member/login?error")
                .permitAll());

        // 3) 로그아웃 : POST /member/logout
        http.logout(logout -> logout
                .logoutUrl("/member/logout")
                .logoutSuccessUrl("/member/login?logout")
                .invalidateHttpSession(true));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

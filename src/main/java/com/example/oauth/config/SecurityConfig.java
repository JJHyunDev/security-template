package com.example.oauth.config;

import com.example.oauth.domain.security.oauth.service.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. 권한 설정
            .authorizeHttpRequests(auth -> auth
                // 로그인 페이지와 정적 리소스는 누구나 접근 가능
                .requestMatchers("/login", "/css/**", "/images/**", "/js/**", "/favicon.ico", "/error").permitAll()
                // 그 외 모든 요청은 인증 필요
                .anyRequest().authenticated()
            )
            // 2. OAuth2 로그인 설정
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login") // 커스텀 로그인 페이지 경로 지정
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService) // 사용자 정보 처리 서비스 등록
                )
            )
            // 3. 로그아웃 설정
            .logout(logout -> logout
                .logoutUrl("/logout") // 로그아웃 요청을 처리할 URL (기본값)
                .logoutSuccessUrl("/") // 로그아웃 성공 시 이동할 URL (여기서 "/"로 설정)
                .invalidateHttpSession(true) // 세션 무효화
                .deleteCookies("JSESSIONID") // 쿠키 삭제
            );

        return http.build();
    }
}
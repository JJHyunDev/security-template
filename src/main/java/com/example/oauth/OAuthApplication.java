package com.example.oauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Spring Boot OAuth2 Application Main Class
 *
 * Role:
 * - Spring Boot 애플리케이션의 진입점(Entry Point)
 * - 내장 톰캣 서버를 시작하고 Spring 컨텍스트 초기화
 *
 * Complexity/Rationale:
 * 1. @SpringBootApplication 어노테이션:
 *    - @Configuration: Spring 설정 클래스임을 명시
 *    - @EnableAutoConfiguration: Spring Boot의 자동 설정 활성화
 *      * OAuth2 관련 자동 설정이 application.yml의 spring.security.oauth2 속성을 감지하여 자동 구성
 *    - @ComponentScan: com.example.oauth 패키지 하위의 모든 컴포넌트를 스캔하여 빈으로 등록
 *
 * 2. Auto-Configuration:
 *    - OAuth2ClientAutoConfiguration: OAuth2 클라이언트 자동 설정
 *    - SecurityAutoConfiguration: Spring Security 기본 설정
 *    - ThymeleafAutoConfiguration: Thymeleaf 템플릿 엔진 설정
 *
 * 3. Application Context:
 *    - SecurityConfig, HomeController 등이 자동으로 빈으로 등록됨
 *    - OAuth2AuthorizedClientRepository, OAuth2AuthorizedClientService 등 OAuth2 관련 빈이 자동 생성됨
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class OAuthApplication {

    /**
     * 애플리케이션 메인 메서드
     *
     * Role:
     * - Spring Boot 애플리케이션을 시작하는 진입점
     *
     * Complexity/Rationale:
     * - SpringApplication.run()이 내장 톰캣 서버를 시작하고 포트 8080에서 리스닝
     * - Docker 환경에서는 spring-boot 컨테이너 내부에서 실행됨
     * - Nginx가 외부 요청을 받아 이 애플리케이션으로 프록시
     *
     * @param args 커맨드 라인 인자
     */
    public static void main(String[] args) {
        SpringApplication.run(OAuthApplication.class, args);
    }
}

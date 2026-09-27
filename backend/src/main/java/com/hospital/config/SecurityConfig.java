package com.hospital.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.ErrorCode;
import com.hospital.common.Result;
import com.hospital.filter.JwtAuthenticationFilter;
import com.hospital.security.LoginPatient;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/login", "/auth/captcha", "/auth/wechat-login").permitAll()
                // 小程序端接口：只认患者 token，userId 一律从 token 取（SecurityUtils）
                .requestMatchers("/user/**").hasRole(LoginPatient.ROLE)
                // 其余都是管理后台接口：只认员工角色。
                // 患者 token 若放到这里来会被 403 挡掉——T07 之前是 anyRequest().authenticated()，
                // 患者 token 能直接打到 /demo、/payments，属于越权口子。
                // 角色名与 V2__init_admin.sql 里 role 表的 4 行一致，新增角色要同步这里。
                .anyRequest().hasAnyRole("system", "admin", "doctor", "nurse")
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter().write(objectMapper.writeValueAsString(
                            Result.error(ErrorCode.UNAUTHORIZED)));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter().write(objectMapper.writeValueAsString(
                            Result.error(ErrorCode.PERMISSION_DENIED)));
                })
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

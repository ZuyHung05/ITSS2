package com.example.demo.config;

import com.example.demo.repository.HanareRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() {
        var encoder=(DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        encoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return encoder;
    }
    @Bean UserDetailsService users(HanareRepository repository) {
        return email -> repository.login(email.strip().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a.requestMatchers("/login","/assets/**","/error").permitAll().anyRequest().authenticated())
            .formLogin(f -> f.loginPage("/login").usernameParameter("email").defaultSuccessUrl("/projects",true).permitAll())
            .logout(l -> l.logoutSuccessUrl("/login?logout"))
            .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor((req,res,ex) -> {
                res.setStatus(401);res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                res.getWriter().write("{\"success\":false,\"error\":{\"code\":\"UNAUTHENTICATED\",\"message\":\"Please sign in.\"}}");
            },PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
            .accessDeniedHandler((req,res,ex) -> {
                res.setStatus(403);res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                res.getWriter().write("{\"success\":false,\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"Access denied or CSRF token missing. Refresh and try again.\"}}");
            }));
        return http.build();
    }
    public static class Principal extends User {
        private final UUID id;
        private final String displayName;
        public Principal(UUID id,String name,String email,String hash) {
            super(email,hash,java.util.List.of());this.id=id;this.displayName=name;
        }
        public UUID id() { return id; }
        public String displayName() { return displayName; }
    }
}

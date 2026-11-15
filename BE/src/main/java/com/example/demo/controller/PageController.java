package com.example.demo.controller;

import com.example.demo.config.SecurityConfig.Principal;
import com.example.demo.exception.ApiException;
import com.example.demo.service.HanareService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class PageController {
    private final HanareService service;
    public PageController(HanareService service) { this.service=service; }
    @GetMapping("/") public String home() { return "redirect:/projects"; }
    @GetMapping("/login") public String login() { return "login"; }
    @GetMapping("/projects") public String projects() { return "app"; }
    @GetMapping({"/projects/{id}","/projects/{id}/tasks","/projects/{id}/checkpoint","/projects/{id}/team-progress","/projects/{id}/review","/projects/{id}/tasks/{task}"})
    public String project(@PathVariable UUID id,@PathVariable(required=false) UUID task,@AuthenticationPrincipal Principal p,jakarta.servlet.http.HttpServletRequest request) {
        try {
            service.project(id,p.id());
            if(request.getRequestURI().endsWith("/review")) service.lead(id,p.id());
            if(task!=null&&!service.detail(task,p.id()).task().projectId().equals(id)) throw ApiException.missing();
        } catch(ApiException e) { throw new ResponseStatusException(HttpStatus.valueOf(e.status()),e.getMessage()); }
        return "app";
    }
}

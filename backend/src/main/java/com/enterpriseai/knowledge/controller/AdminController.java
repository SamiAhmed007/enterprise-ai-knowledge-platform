package com.enterpriseai.knowledge.controller;

import com.enterpriseai.knowledge.dto.AdminDtos.AdminUser;
import com.enterpriseai.knowledge.dto.AdminDtos.AnalyticsOverview;
import com.enterpriseai.knowledge.dto.AdminDtos.DashboardStats;
import com.enterpriseai.knowledge.dto.DocumentDtos.DocumentResponse;
import com.enterpriseai.knowledge.service.AdminService;
import com.enterpriseai.knowledge.dto.WorkspaceDtos.WorkspaceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/stats")
    public DashboardStats stats() {
        return admin.stats();
    }

    @GetMapping("/analytics")
    public AnalyticsOverview analytics() {
        return admin.analytics();
    }

    @GetMapping("/users")
    public List<AdminUser> users() {
        return admin.users();
    }

    @GetMapping("/documents")
    public List<DocumentResponse> documents() {
        return admin.documents();
    }

    @GetMapping("/workspaces")
    public List<WorkspaceResponse> workspaces() {
        return admin.workspaces();
    }
}

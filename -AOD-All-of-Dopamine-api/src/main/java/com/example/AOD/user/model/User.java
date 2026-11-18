package com.example.AOD.user.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> roles = new ArrayList<>();

    /** 가입 때 동의한 처리방침 버전과 시각. 동의 기능 이전 클라이언트로 가입하면 비어 있다. */
    @Column(name = "policy_version", length = 20)
    private String policyVersion;

    @Column(name = "policy_agreed_at")
    private LocalDateTime policyAgreedAt;

    // Getters
    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public List<String> getRoles() {
        return roles;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public LocalDateTime getPolicyAgreedAt() {
        return policyAgreedAt;
    }

    // Setters
    public void setPolicyVersion(String policyVersion) {
        this.policyVersion = policyVersion;
    }

    public void setPolicyAgreedAt(LocalDateTime policyAgreedAt) {
        this.policyAgreedAt = policyAgreedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}


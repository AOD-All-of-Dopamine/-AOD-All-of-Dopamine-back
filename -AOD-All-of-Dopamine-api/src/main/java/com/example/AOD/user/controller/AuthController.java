package com.example.AOD.user.controller;

import com.example.AOD.security.JwtTokenProvider;
import com.example.AOD.user.dto.LoginRequest;
import com.example.AOD.user.dto.SignUpRequest;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;


@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:3000")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @PostMapping("/signup")
    @Transactional
    public ResponseEntity<?> registerUser(@RequestBody SignUpRequest signUpRequest) {
        // 유저네임, 이메일 중복 검사
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("error", "이미 사용 중인 아이디입니다."));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "이미 사용 중인 이메일입니다."));
        }

        try {
            // 새 유저 생성
            User user = new User();
            user.setUsername(signUpRequest.getUsername());
            user.setEmail(signUpRequest.getEmail());
            user.setPassword(passwordEncoder.encode(signUpRequest.getPassword()));
            user.setRoles(Collections.singletonList("ROLE_USER"));

            userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "message", "회원가입이 완료되었습니다.",
                    "username", user.getUsername()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "회원가입 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        try {
            User user = userRepository.findByUsername(loginRequest.getUsername())
                    .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

            if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
                return ResponseEntity.badRequest().body(Map.of("error", "잘못된 비밀번호입니다."));
            }

            String token = jwtTokenProvider.createToken(user.getUsername(), user.getRoles());

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "username", user.getUsername(),
                    "userId", user.getId(),
                    "message", "로그인 성공!"
            ));
        } catch (AuthenticationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "로그인에 실패했습니다."));
        }
    }

    @PostMapping("/check-duplicate")
    public ResponseEntity<?> checkDuplicate(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String email = request.get("email");

            boolean usernameExists = false;
            boolean emailExists = false;

            // 사용자명 중복 체크
            if (username != null && !username.trim().isEmpty()) {
                usernameExists = userRepository.existsByUsername(username.trim());
            }

            // 이메일 중복 체크
            if (email != null && !email.trim().isEmpty()) {
                emailExists = userRepository.existsByEmail(email.trim());
            }

            // 중복이 있는 경우
            if (usernameExists || emailExists) {
                Map<String, Object> response = new java.util.HashMap<>();
                response.put("usernameExists", usernameExists);
                response.put("emailExists", emailExists);

                String message = "";
                if (usernameExists && emailExists) {
                    message = "이미 사용 중인 아이디와 이메일입니다.";
                } else if (usernameExists) {
                    message = "이미 사용 중인 아이디입니다.";
                } else if (emailExists) {
                    message = "이미 사용 중인 이메일입니다.";
                }
                response.put("message", message);

                return ResponseEntity.badRequest().body(response);
            }

            // 중복이 없는 경우
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("usernameExists", false);
            response.put("emailExists", false);
            response.put("message", "사용 가능합니다.");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("message", "서버 오류가 발생했습니다.");
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 현재 로그인한 사용자 정보 조회
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7); // "Bearer " 제거
            String username = jwtTokenProvider.getUsername(token);
            
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
            
            return ResponseEntity.ok(Map.of(
                    "userId", user.getId(),
                    "username", user.getUsername(),
                    "email", user.getEmail()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "인증에 실패했습니다."));
        }
    }
}



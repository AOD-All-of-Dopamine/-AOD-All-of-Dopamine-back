//package com.example.AOD.user.controller;
//
//import com.example.AOD.recommendation.service.UserPreferenceService;
//import com.example.AOD.security.JwtTokenProvider;
//import com.example.AOD.user.dto.JwtResponse;
//import com.example.AOD.user.dto.LoginRequest;
//import com.example.AOD.user.dto.SignUpRequest;
//import com.example.AOD.user.model.User;
//import com.example.AOD.user.repository.UserRepository;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.core.AuthenticationException;
//import org.springframework.security.core.userdetails.UsernameNotFoundException;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.http.HttpStatus;
//
//import java.util.Collections;
//import java.util.Map;
//import java.util.HashMap;
//
//
//@RestController
//@RequestMapping("/api/auth")
//@CrossOrigin(origins = "http://localhost:3000")
//public class AuthController {
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Autowired
//    private PasswordEncoder passwordEncoder;
//
//    @Autowired
//    private JwtTokenProvider jwtTokenProvider;
//
//    @Autowired
//    private UserPreferenceService userPreferenceService;
//
//    @PostMapping("/signup")
//    @Transactional
//    public ResponseEntity<?> registerUser(@RequestBody SignUpRequest signUpRequest) {
//        // 유저네임, 이메일 중복 검사
//        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
//            return ResponseEntity.badRequest().body(Map.of("error", "이미 사용 중인 아이디입니다."));
//        }
//
//        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
//            return ResponseEntity.badRequest().body(Map.of("error", "이미 사용 중인 이메일입니다."));
//        }
//
//        try {
//            // 새 유저 생성
//            User user = new User();
//            user.setUsername(signUpRequest.getUsername());
//            user.setEmail(signUpRequest.getEmail());
//            user.setPassword(passwordEncoder.encode(signUpRequest.getPassword()));
//            user.setRoles(Collections.singletonList("ROLE_USER"));
//
//            userRepository.save(user);
//
//            // 사용자 선호도 생성 (선호도 정보가 있는 경우에만)
//            boolean hasPreferences = false;
//            if (hasPreferenceData(signUpRequest)) {
//                userPreferenceService.createInitialPreference(user.getUsername(), signUpRequest);
//                hasPreferences = true;
//            }
//
//            return ResponseEntity.ok(Map.of(
//                    "message", "회원가입이 완료되었습니다.",
//                    "username", user.getUsername(),
//                    "hasPreferences", hasPreferences
//            ));
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body(Map.of("error", "회원가입 중 오류가 발생했습니다: " + e.getMessage()));
//        }
//    }
//
//    @PostMapping("/login")
//    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
//        try {
//            User user = userRepository.findByUsername(loginRequest.getUsername())
//                    .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
//
//            if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
//                return ResponseEntity.badRequest().body(Map.of("error", "잘못된 비밀번호입니다."));
//            }
//
//            String token = jwtTokenProvider.createToken(user.getUsername(), user.getRoles());
//
//            // 사용자가 선호도를 설정했는지 확인
//            boolean hasPreferences = userPreferenceService.getUserPreference(user.getUsername()).isPresent();
//
//            return ResponseEntity.ok(Map.of(
//                    "token", token,
//                    "username", user.getUsername(),
//                    "hasPreferences", hasPreferences,
//                    "message", hasPreferences ? "로그인 성공!" : "로그인 성공! 선호도를 설정하시면 더 나은 추천을 받으실 수 있어요."
//            ));
//        } catch (AuthenticationException e) {
//            return ResponseEntity.badRequest().body(Map.of("error", "로그인에 실패했습니다."));
//        }
//    }
//
//    private boolean hasPreferenceData(SignUpRequest signUpRequest) {
//        return (signUpRequest.getPreferredGenres() != null && !signUpRequest.getPreferredGenres().isEmpty()) ||
//                (signUpRequest.getPreferredContentTypes() != null && !signUpRequest.getPreferredContentTypes().isEmpty()) ||
//                signUpRequest.getAgeGroup() != null ||
//                signUpRequest.getPreferredAgeRating() != null ||
//                signUpRequest.getFavoriteDirectors() != null ||
//                signUpRequest.getFavoriteAuthors() != null ||
//                signUpRequest.getFavoriteActors() != null ||
//                signUpRequest.getLikesNewContent() != null ||
//                signUpRequest.getLikesClassicContent() != null ||
//                signUpRequest.getAdditionalNotes() != null;
//    }
//
//    @PostMapping("/check-duplicate")
//    public ResponseEntity<?> checkDuplicate(@RequestBody Map<String, String> request) {
//        try {
//            String username = request.get("username");
//            String email = request.get("email");
//
//            boolean usernameExists = false;
//            boolean emailExists = false;
//
//            // 사용자명 중복 체크
//            if (username != null && !username.trim().isEmpty()) {
//                usernameExists = userRepository.existsByUsername(username.trim());
//            }
//
//            // 이메일 중복 체크
//            if (email != null && !email.trim().isEmpty()) {
//                emailExists = userRepository.existsByEmail(email.trim());
//            }
//
//            // 중복이 있는 경우
//            if (usernameExists || emailExists) {
//                Map<String, Object> response = new java.util.HashMap<>();
//                response.put("usernameExists", usernameExists);
//                response.put("emailExists", emailExists);
//
//                String message = "";
//                if (usernameExists && emailExists) {
//                    message = "이미 사용 중인 아이디와 이메일입니다.";
//                } else if (usernameExists) {
//                    message = "이미 사용 중인 아이디입니다.";
//                } else if (emailExists) {
//                    message = "이미 사용 중인 이메일입니다.";
//                }
//                response.put("message", message);
//
//                return ResponseEntity.badRequest().body(response);
//            }
//
//            // 중복이 없는 경우
//            Map<String, Object> response = new java.util.HashMap<>();
//            response.put("usernameExists", false);
//            response.put("emailExists", false);
//            response.put("message", "사용 가능합니다.");
//
//            return ResponseEntity.ok(response);
//
//        } catch (Exception e) {
//            Map<String, Object> response = new java.util.HashMap<>();
//            response.put("message", "서버 오류가 발생했습니다.");
//            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).body(response);
//        }
//    }
//}
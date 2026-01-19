package com.tathang.example304.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.tathang.example304.dto.LoginDto;
import com.tathang.example304.dto.RegisterDto;
import com.tathang.example304.payload.request.ResetPasswordOtpRequest;
import com.tathang.example304.dto.UpdateProfileRequest;
import com.tathang.example304.model.ERole;
import com.tathang.example304.model.Role;
import com.tathang.example304.model.User;
import com.tathang.example304.model.ResetPasswordToken;
import com.tathang.example304.payload.response.JwtResponse;
import com.tathang.example304.repository.RoleRepository;
import com.tathang.example304.repository.UserRepository;
import com.tathang.example304.repository.ResetPasswordTokenRepository;
import com.tathang.example304.security.jwt.JwtUtils;
import com.tathang.example304.security.services.UserDetailsImpl;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final ResetPasswordTokenRepository resetTokenRepo;
    private final JavaMailSender mailSender;
    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ResetPasswordTokenRepository tokenRepo;

    public AuthController(UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtUtils jwtUtils,
            AuthenticationManager authenticationManager,
            ResetPasswordTokenRepository resetTokenRepo,
            JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.authenticationManager = authenticationManager;
        this.resetTokenRepo = resetTokenRepo;
        this.mailSender = mailSender;
    }

    // ✅ THÊM SLASH VÀO ĐẦU CÁC POST MAPPING
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterDto registerDto) {
        System.out.println("=== REGISTER ===");
        System.out.println("Username: " + registerDto.getUsername());
        System.out.println("Email: " + registerDto.getEmail());
        System.out.println("Password: " + registerDto.getPassword());
        System.out.println("Requested Roles: " + registerDto.getRoles());

        if (userRepository.existsByUsername(registerDto.getUsername())) {
            return new ResponseEntity<>("Username is taken!", HttpStatus.BAD_REQUEST);
        }

        if (userRepository.existsByEmail(registerDto.getEmail())) {
            return new ResponseEntity<>("Email is already in use!", HttpStatus.BAD_REQUEST);
        }

        User user = new User();
        user.setUsername(registerDto.getUsername());
        user.setEmail(registerDto.getEmail());
        user.setPassword(passwordEncoder.encode(registerDto.getPassword()));

        Set<Role> roles = new HashSet<>();

        // XỬ LÝ ROLE THEO REQUEST
        if (registerDto.getRoles() == null || registerDto.getRoles().isEmpty()) {
            // Nếu không có role nào được chỉ định, mặc định là USER
            Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: USER Role is not found."));
            roles.add(userRole);
            System.out.println("Assigning default USER role");
        } else {
            // Xử lý các role được chỉ định
            for (String roleName : registerDto.getRoles()) {
                try {
                    // Chuyển đổi role name sang ERole enum
                    String roleEnumName = "ROLE_" + roleName.toUpperCase();
                    ERole roleEnum = ERole.valueOf(roleEnumName);

                    Role role = roleRepository.findByName(roleEnum)
                            .orElseThrow(() -> new RuntimeException("Error: Role " + roleName + " is not found."));
                    roles.add(role);
                    System.out.println("Assigning role: " + roleName);
                } catch (IllegalArgumentException e) {
                    System.out.println("Invalid role requested: " + roleName);
                    return new ResponseEntity<>("Invalid role: " + roleName, HttpStatus.BAD_REQUEST);
                }
            }
        }

        user.setRoles(roles);
        User savedUser = userRepository.save(user);

        System.out.println("User registered successfully with ID: " + savedUser.getId());
        System.out.println("Assigned roles: " + roles.stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toList()));
        return new ResponseEntity<>("User registered success!", HttpStatus.OK);
    }

    // ✅ THÊM SLASH VÀO ĐẦU
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDto loginDto) {
        try {
            System.out.println("🔐 LOGIN ATTEMPT ==================================");
            System.out.println("Username: " + loginDto.getUsername());

            // 🆕 KIỂM TRA USER TỒN TẠI VÀ LẤY FULL NAME
            User user = userRepository.findByUsername(loginDto.getUsername())
                    .orElseThrow(() -> {
                        System.out.println("❌ USER NOT FOUND: " + loginDto.getUsername());
                        return new RuntimeException("User not found");
                    });

            System.out.println("✅ User found: " + user.getUsername());
            System.out.println("👤 Full Name: " + user.getFullName()); // 🆕 THÊM LOG FULL NAME
            System.out.println("👥 Roles count: " + user.getRoles().size());
            user.getRoles().forEach(role -> System.out.println("   - Role: " + role.getName()));

            // Authentication
            System.out.println("🔄 Attempting authentication...");
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginDto.getUsername(), loginDto.getPassword()));

            System.out.println("🎉 Authentication SUCCESS!");

            // Generate JWT
            System.out.println("🔑 Generating JWT token...");
            String jwt = jwtUtils.generateJwtToken(authentication);
            System.out.println("✅ JWT Token generated, length: " + jwt.length());

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            System.out.println("✅ Login successful! User: " + userDetails.getUsername());
            System.out.println("✅ Full Name: " + user.getFullName()); // 🆕 LOG FULL NAME
            System.out.println("✅ Roles: " + roles);
            System.out.println("==================================================");

            // 🆕 TRẢ VỀ FULL NAME TRONG RESPONSE - DÙNG CONSTRUCTOR MỚI
            return ResponseEntity.ok(new JwtResponse(
                    jwt,
                    userDetails.getId(),
                    userDetails.getUsername(),
                    user.getFullName(), // 🆕 THÊM FULL NAME
                    userDetails.getEmail(),
                    roles));

        } catch (Exception e) {
            System.out.println("❌ LOGIN FAILED: " + e.getMessage());
            e.printStackTrace();
            System.out.println("==================================================");
            return new ResponseEntity<>("Invalid username or password! Error: " + e.getMessage(),
                    HttpStatus.UNAUTHORIZED);
        }
    }

    @Transactional
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email not found"));

        String otp = String.valueOf(new Random().nextInt(900000) + 100000);

        ResetPasswordToken token = tokenRepo.findByUser(user)
                .orElse(new ResetPasswordToken());

        token.setUser(user);
        token.setToken(otp);
        token.setExpiryDate(LocalDateTime.now().plusMinutes(5));
        tokenRepo.save(token);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Mã OTP đặt lại mật khẩu");
        message.setText("Mã OTP của bạn là: " + otp);

        mailSender.send(message);
        return ResponseEntity.ok("OTP sent");
    }

    @PostMapping("/reset-password-otp")
    @Transactional
    public ResponseEntity<?> resetPasswordOtp(
            @RequestBody ResetPasswordOtpRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Email không tồn tại"));

        ResetPasswordToken token = resetTokenRepo.findByUser(user)
                .orElseThrow(() -> new RuntimeException("OTP không tồn tại"));

        if (!token.getToken().equals(request.getOtp())) {
            return ResponseEntity.badRequest().body("OTP sai");
        }

        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body("OTP đã hết hạn");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        resetTokenRepo.delete(token);

        return ResponseEntity.ok("Đổi mật khẩu thành công");
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        response.put("fullName", user.getFullName());
        response.put("imageUrl", user.getImageUrl()); // 🔥 BẮT BUỘC
        response.put("phone", user.getPhone());
        response.put("address", user.getAddress());
        response.put("roles", user.getRoles().stream()
                .map(r -> r.getName().name())
                .toList());

        return ResponseEntity.ok(response);
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestBody UpdateProfileRequest request) {

        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setAddress(request.getAddress());
        user.setImageUrl(request.getImageUrl()); // 🔥 LƯU ẢNH

        userRepository.save(user);

        return ResponseEntity.ok("Profile updated");
    }

}
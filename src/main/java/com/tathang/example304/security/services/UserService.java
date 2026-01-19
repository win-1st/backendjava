package com.tathang.example304.security.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.tathang.example304.model.ERole;
import com.tathang.example304.model.Role;
import com.tathang.example304.model.User;
import com.tathang.example304.repository.RoleRepository;
import com.tathang.example304.repository.UserRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> getAllStaffs() {
        return userRepository.findByRolesNameIn(
                List.of(ERole.ROLE_USER, ERole.ROLE_ADMIN));
    }

    public User createStaff(String username, String email, String password, String fullName,
            String phone, String address, String imageUrl, List<String> roles) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username đã tồn tại");
        }
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email đã tồn tại");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setAddress(address);
        user.setImageUrl(imageUrl);
        user.setIsActive(true);

        Set<Role> roleSet = new HashSet<>();
        for (String roleName : roles) {
            String formattedRoleName = roleName.toUpperCase();
            if (!formattedRoleName.startsWith("ROLE_")) {
                formattedRoleName = "ROLE_" + formattedRoleName;
            }

            Role role = roleRepository.findByName(ERole.valueOf(formattedRoleName))
                    .orElseThrow(() -> new RuntimeException("Role không tồn tại: " + roleName));
            roleSet.add(role);
        }
        user.setRoles(roleSet);

        return userRepository.save(user);
    }

    public User updateStaff(Long id, String username, String email, String password,
            String fullName, String phone, String address, String imageUrl, List<String> roles) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User không tồn tại với ID: " + id));

        if (!user.getUsername().equals(username) && userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username đã tồn tại");
        }
        if (!user.getEmail().equals(email) && userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email đã tồn tại");
        }

        user.setUsername(username);
        user.setEmail(email);
        if (password != null && !password.isEmpty()) {
            user.setPassword(passwordEncoder.encode(password));
        }
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setAddress(address);
        if (imageUrl != null) {
            user.setImageUrl(imageUrl);
        }

        Set<Role> roleSet = new HashSet<>();
        for (String roleName : roles) {
            String formattedRoleName = roleName.toUpperCase();
            if (!formattedRoleName.startsWith("ROLE_")) {
                formattedRoleName = "ROLE_" + formattedRoleName;
            }

            Role role = roleRepository.findByName(ERole.valueOf(formattedRoleName))
                    .orElseThrow(() -> new RuntimeException("Role không tồn tại: " + roleName));
            roleSet.add(role);
        }
        user.setRoles(roleSet);

        return userRepository.save(user);
    }

    public User toggleUserStatus(Long id, Boolean isActive) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User không tồn tại với ID: " + id));
        user.setIsActive(isActive);
        return userRepository.save(user);
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User không tồn tại với ID: " + id));
    }

    public boolean deleteUser(Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }

}
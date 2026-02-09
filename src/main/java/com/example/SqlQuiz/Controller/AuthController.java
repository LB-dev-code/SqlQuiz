package com.example.SqlQuiz.Controller;

import com.example.SqlQuiz.entity.User;
import com.example.SqlQuiz.service.EmailService;
import com.example.SqlQuiz.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;
    
    // Home page
    @GetMapping("/")
    public String home() {
        // Get current logged-in user from Spring Security
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // If user is logged in, redirect to appropriate dashboard
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return "redirect:/dashboard";
        }

        // Otherwise show home page
        return "index";
    }

    // Home page (explicit mapping)
    @GetMapping("/index")
    public String indexPage() {
        return "index";
    }

    // Login page
    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                           @RequestParam(value = "logout", required = false) String logout,
                           Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid username or password!");
        }
        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully!");
        }
        return "auth/login";
    }

    // Registration page
    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("user", new User());
        return "auth/register";
    }

    // Send verification code
    @PostMapping("/send-verification-code")
    @ResponseBody
    public Map<String, Object> sendVerificationCode(@RequestParam String email) {
        log.info("[AuthController] Received verification code request, email: {}", email);
        Map<String, Object> response = new HashMap<>();
        try {
            emailService.sendVerificationCode(email);
            response.put("success", true);
            response.put("message", "Verification code has been sent to your email");
            log.info("[AuthController] Verification code sent successfully");
        } catch (Exception e) {
            log.error("[AuthController] Verification code send failed", e);
            response.put("success", false);
            response.put("message", "Failed to send verification code: " + e.getMessage());
        }
        return response;
    }

    // Process registration
    @PostMapping("/register")
    public String processRegister(@RequestParam String username,
                                @RequestParam String password,
                                @RequestParam String confirmPassword,
                                @RequestParam String email,
                                @RequestParam String verificationCode,
                                @RequestParam String fullName,
                                @RequestParam String role,
                                RedirectAttributes redirectAttributes) {
        try {
            // Validate password confirmation
            if (!password.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "Passwords do not match!");
                return "redirect:/register";
            }

            // Verify email verification code
            if (!emailService.verifyCode(email, verificationCode)) {
                redirectAttributes.addFlashAttribute("error", "Verification code is invalid or expired!");
                return "redirect:/register";
            }

            // Validate role
            User.Role userRole;
            try {// Check if role matches the fields defined in Role enum
                userRole = User.Role.valueOf(role.toUpperCase());
            } catch (IllegalArgumentException e) {
                redirectAttributes.addFlashAttribute("error", "Invalid user role!");
                return "redirect:/register";
            }

            // Register user
            userService.registerUser(username, password, email, fullName, userRole);

            // Cleanup verification code
            emailService.cleanupAfterRegistration(email);

            redirectAttributes.addFlashAttribute("message", "Registration successful! Please login.");
            return "redirect:/login";

        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        }
    }

    // Dashboard
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            User user = (User) auth.getPrincipal();
            model.addAttribute("user", user);

            // Redirect to appropriate dashboard based on user role
            if (user.getRole() == User.Role.TEACHER) {
                return "redirect:/teacher/dashboard";
            } else if (user.getRole() == User.Role.STUDENT) {
                return "redirect:/student/dashboard";
            }
        }

        return "redirect:/login";
    }
}

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
    
    // 首页
    @GetMapping("/")
    public String home() {
        //从 Spring Security 里拿到当前登录用户的信息（）
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        // 如果用户已登录，重定向到相应的仪表板
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return "redirect:/dashboard";
        }
        
        // 否则显示主页
        return "index";
    }
    
    // 主页（显式映射）
    @GetMapping("/index")
    public String indexPage() {
        return "index";
    }
    
    // 登录页面
    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                           @RequestParam(value = "logout", required = false) String logout,
                           Model model) {
        if (error != null) {
            model.addAttribute("error", "用户名或密码错误！");
        }
        if (logout != null) {
            model.addAttribute("message", "您已成功登出！");
        }
        return "auth/login";
    }
    
    // 注册页面
    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("user", new User());
        return "auth/register";
    }
    
    // 发送验证码
    @PostMapping("/send-verification-code")
    @ResponseBody
    public Map<String, Object> sendVerificationCode(@RequestParam String email) {
        log.info("[AuthController] 收到发送验证码请求，邮箱: {}", email);
        Map<String, Object> response = new HashMap<>();
        try {
            emailService.sendVerificationCode(email);
            response.put("success", true);
            response.put("message", "验证码已发送到您的邮箱");
            log.info("[AuthController] 验证码发送成功");
        } catch (Exception e) {
            log.error("[AuthController] 验证码发送失败", e);
            response.put("success", false);
            response.put("message", "发送验证码失败: " + e.getMessage());
        }
        return response;
    }

    // 处理注册
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
            // 验证密码确认
            if (!password.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "两次输入的密码不一致！");
                return "redirect:/register";
            }

            // 验证邮箱验证码
            if (!emailService.verifyCode(email, verificationCode)) {
                redirectAttributes.addFlashAttribute("error", "验证码错误或已过期！");
                return "redirect:/register";
            }

            // 验证角色
            User.Role userRole;
            try {//检验和role里面定义的字段是否一样
                userRole = User.Role.valueOf(role.toUpperCase());
            } catch (IllegalArgumentException e) {
                redirectAttributes.addFlashAttribute("error", "无效的用户角色！");
                return "redirect:/register";
            }

            // 注册用户
            userService.registerUser(username, password, email, fullName, userRole);

            // 清理验证码
            emailService.cleanupAfterRegistration(email);

            redirectAttributes.addFlashAttribute("message", "注册成功！请登录。");
            return "redirect:/login";

        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        }
    }
    
    // 仪表板
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            User user = (User) auth.getPrincipal();
            model.addAttribute("user", user);

            // 根据用户角色重定向到相应的仪表板
            if (user.getRole() == User.Role.TEACHER) {
                return "redirect:/teacher/dashboard";
            } else if (user.getRole() == User.Role.STUDENT) {
                return "redirect:/student/dashboard";
            }
        }

        return "redirect:/login";
    }
}

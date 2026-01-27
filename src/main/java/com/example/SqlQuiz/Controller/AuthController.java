package com.example.SqlQuiz.Controller;

import com.example.SqlQuiz.entity.User;
import com.example.SqlQuiz.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    
    @Autowired
    private UserService userService;
    
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
    
    // 处理注册
    @PostMapping("/register")
    public String processRegister(@RequestParam String username,
                                @RequestParam String password,
                                @RequestParam String confirmPassword,
                                @RequestParam String email,
                                @RequestParam String fullName,
                                @RequestParam String role,
                                RedirectAttributes redirectAttributes) {
        try {
            // 验证密码确认
            if (!password.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "两次输入的密码不一致！");
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

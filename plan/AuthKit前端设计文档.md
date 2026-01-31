# AuthKit 前端设计文档

## 1. 概述

### 1.1 产品定位
AuthKit 是由 WorkOS 和 Radix 驱动的世界级登录组件，为开发者提供企业级身份认证解决方案的前端界面系统。

### 1.2 设计理念
- **极简主义**：干净、现代的视觉风格
- **品牌适配**：完全可定制以匹配任何应用设计
- **双模式支持**：完整支持浅色/深色主题
- **企业级安全**：内置安全最佳实践的视觉反馈

---

## 2. 设计系统规范

### 2.1 色彩系统

#### 主色调
| 用途 | 浅色模式 | 深色模式 |
|-----|---------|---------|
| 背景色 | `#FFFFFF` / `#F9FAFB` | `#09090B` / `#18181B` |
| 主文字 | `#18181B` | `#FAFAFA` |
| 次要文字 | `#71717A` | `#A1A1AA` |
| 边框 | `#E4E4E7` | `#27272A` |
| 主按钮 | `#18181B` | `#FAFAFA` |
| 主按钮文字 | `#FFFFFF` | `#09090B` |
| 链接色 | `#2563EB` | `#3B82F6` |
| 错误色 | `#DC2626` | `#EF4444` |
| 成功色 | `#16A34A` | `#22C55E` |

#### 语义色彩
```css
/* 浅色模式变量 */
--background-primary: #FFFFFF;
--background-secondary: #F9FAFB;
--background-tertiary: #F4F4F5;

--text-primary: #18181B;
--text-secondary: #71717A;
--text-tertiary: #A1A1AA;

--border-default: #E4E4E7;
--border-hover: #D4D4D8;

--accent-primary: #18181B;
--accent-secondary: #27272A;
```

### 2.2 字体系统

#### 字体家族
```css
--font-sans: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
--font-mono: 'JetBrains Mono', 'Fira Code', monospace;
```

#### 字体大小
| 用途 | 大小 | 行高 | 字重 |
|-----|------|------|------|
| 标题 H1 | 48px | 1.1 | 600 |
| 标题 H2 | 36px | 1.2 | 600 |
| 标题 H3 | 24px | 1.3 | 600 |
| 正文大 | 18px | 1.5 | 400 |
| 正文 | 16px | 1.5 | 400 |
| 正文小 | 14px | 1.5 | 400 |
| 说明 | 12px | 1.4 | 400 |

### 2.3 间距系统
```css
--space-1: 4px;
--space-2: 8px;
--space-3: 12px;
--space-4: 16px;
--space-5: 20px;
--space-6: 24px;
--space-8: 32px;
--space-10: 40px;
--space-12: 48px;
--space-16: 64px;
--space-20: 80px;
```

### 2.4 圆角系统
```css
--radius-sm: 6px;
--radius-md: 8px;
--radius-lg: 12px;
--radius-xl: 16px;
--radius-2xl: 24px;
--radius-full: 9999px;
```

### 2.5 阴影系统
```css
--shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.05);
--shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
--shadow-lg: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
--shadow-xl: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
```

---

## 3. 页面布局结构

### 3.1 整体页面架构
```
┌─────────────────────────────────────────┐
│           Navigation Bar                │
├─────────────────────────────────────────┤
│                                         │
│           Hero Section                  │
│     (Video Demo + Main Headline)        │
│                                         │
├─────────────────────────────────────────┤
│                                         │
│        Features Grid Section            │
│    (6 Feature Cards in 3x2 Grid)        │
│                                         │
├─────────────────────────────────────────┤
│                                         │
│      Flexibility Dashboard Section      │
│     (Dashboard Mockup + Description)    │
│                                         │
├─────────────────────────────────────────┤
│                                         │
│      Customization Options Section      │
│   (5 Customization Card Previews)       │
│                                         │
├─────────────────────────────────────────┤
│                                         │
│         Radix Integration               │
│     (Partner Logos + Description)       │
│                                         │
├─────────────────────────────────────────┤
│                                         │
│       Enterprise Features               │
│  (5 Feature Cards with Visuals)         │
│                                         │
├─────────────────────────────────────────┤
│                                         │
│        WorkOS Integration               │
│    (Platform Overview + CTA)            │
│                                         │
├─────────────────────────────────────────┤
│                                         │
│         Testimonials                    │
│      (2 Customer Quotes)                │
│                                         │
├─────────────────────────────────────────┤
│                                         │
│           Footer CTA                    │
│                                         │
└─────────────────────────────────────────┘
```

### 3.2 响应式断点
```css
--breakpoint-sm: 640px;
--breakpoint-md: 768px;
--breakpoint-lg: 1024px;
--breakpoint-xl: 1280px;
--breakpoint-2xl: 1536px;
```

---

## 4. 核心组件设计

### 4.1 登录卡片组件

#### 基础结构
```
┌────────────────────────────────────┐
│         [Logo 64x64]               │
├────────────────────────────────────┤
│                                    │
│    Welcome to [App Name]           │  ← H2, 24px, 600
│                                    │
│   Log in to continue.              │  ← 正文小, 14px
│                                    │
│   ┌────────────────────────────┐  │
│   │ Email                      │  │  ← 输入框标签
│   ├────────────────────────────┤  │
│   │                            │  │
│   └────────────────────────────┘  │
│                                    │
│   ┌────────────────────────────┐  │
│   │ Password                   │  │
│   ├────────────────────────────┤  │
│   │                            │  │
│   └────────────────────────────┘  │
│                                    │
│   ┌───────────────────────────┐  │
│   │      Sign in              │  │  ← 主按钮
│   └───────────────────────────┘  │
│                                    │
│   Don't have an account? Sign up   │  ← 链接
│                                    │
└────────────────────────────────────┘
```

#### 尺寸规范
| 属性 | 值 |
|-----|-----|
| 卡片宽度 | 400px (最大) |
| 卡片内边距 | 32px |
| 卡片圆角 | 16px |
| 卡片阴影 | `--shadow-xl` |
| 输入框高度 | 44px |
| 按钮高度 | 44px |

#### 状态变体
```css
/* 浅色模式 */
.login-card {
  background: var(--background-primary);
  border: 1px solid var(--border-default);
  box-shadow: var(--shadow-xl);
}

/* 深色模式 */
.login-card[data-theme="dark"] {
  background: var(--background-secondary);
  border: 1px solid var(--border-hover);
}
```

### 4.2 多因素认证卡片

#### 结构
```
┌────────────────────────────────────┐
│         [Logo 64x64]               │
├────────────────────────────────────┤
│                                    │
│     Sign in to [App Name]          │
│                                    │
│  Enter the temporary passcode      │
│  from your authenticator app.      │
│                                    │
│   ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐  │  ← 验证码输入框
│   │   │ │   │ │   │ │   │ │   │  │
│   └───┘ └───┘ └───┘ └───┘ └───┘  │
│                                    │
│   ┌───────────────────────────┐  │
│   │      Verify               │  │
│   └───────────────────────────┘  │
│                                    │
│       Return to sign in            │
│                                    │
└────────────────────────────────────┘
```

#### 验证码输入框
- 尺寸：48x48px
- 间距：8px
- 圆角：8px
- 字体大小：24px
- 字体：等宽字体
- 自动聚焦下一个输入框

### 4.3 功能特性卡片

#### 结构
```
┌─────────────────────────────┐
│                             │
│      [Icon 64x64]           │  ← SVG 图标
│                             │
│    Single Sign-On           │  ← 标题, 18px
│                             │
└─────────────────────────────┘
```

#### 悬停效果
```css
.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}
```

### 4.4 定制选项预览卡片

#### 展示内容
1. **页面背景色预览**：展示不同背景色下的登录卡片
2. **外观模式预览**：浅色/深色模式对比
3. **Favicon预览**：展示不同品牌图标
4. **按钮颜色预览**：不同主色调的按钮样式
5. **链接颜色预览**：不同链接色的视觉效果

---

## 5. 动画与交互设计

### 5.1 页面过渡动画

#### 淡入效果
```css
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-fade-in {
  animation: fadeIn 0.6s ease-out;
}
```

#### 交错动画
```css
.stagger-item:nth-child(1) { animation-delay: 0ms; }
.stagger-item:nth-child(2) { animation-delay: 100ms; }
.stagger-item:nth-child(3) { animation-delay: 200ms; }
.stagger-item:nth-child(4) { animation-delay: 300ms; }
```

### 5.2 登录卡片动画

#### 模式切换动画
```css
@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.login-card-transition {
  animation: slideIn 0.3s ease-out;
}
```

#### 输入框聚焦动画
```css
.input-field:focus {
  outline: none;
  border-color: var(--accent-primary);
  box-shadow: 0 0 0 3px rgba(24, 24, 27, 0.1);
  transition: all 0.2s ease;
}
```

### 5.3 加载状态动画

#### Spinner
```css
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--border-default);
  border-top-color: var(--accent-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
```

#### Skeleton Loading
```css
@keyframes shimmer {
  0% {
    background-position: -200% 0;
  }
  100% {
    background-position: 200% 0;
  }
}

.skeleton {
  background: linear-gradient(
    90deg,
    var(--background-tertiary) 0%,
    var(--background-secondary) 50%,
    var(--background-tertiary) 100%
  );
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}
```

---

## 6. 品牌定制系统

### 6.1 可定制属性

```typescript
interface AuthKitTheme {
  // 品牌标识
  logo?: string;
  appName: string;
  favicon?: string;

  // 色彩
  primaryColor?: string;
  buttonTextColor?: string;
  linkColor?: string;
  pageBackgroundColor?: string;

  // 外观
  appearance?: 'light' | 'dark' | 'system';

  // 边框样式
  borderRadius?: 'sm' | 'md' | 'lg' | 'xl';

  // 自定义CSS
  customCSS?: string;
}
```

### 6.2 主题配置示例

```typescript
const customTheme: AuthKitTheme = {
  logo: '/logo.svg',
  appName: 'SuperApp',
  favicon: '/favicon.ico',
  primaryColor: '#6366F1',
  buttonTextColor: '#FFFFFF',
  linkColor: '#8B5CF6',
  pageBackgroundColor: '#F8FAFC',
  appearance: 'light',
  borderRadius: 'lg',
  customCSS: `
    .authkit-card {
      backdrop-filter: blur(10px);
    }
  `
};
```

---

## 7. 可访问性设计

### 7.1 WCAG 2.1 合规

#### 色彩对比度
| 元素 | 对比度 | 标准 |
|-----|--------|------|
| 正文文字 | ≥ 4.5:1 | AA 级 |
| 大号文字 (18px+) | ≥ 3:1 | AA 级 |
| 图标/图形 | ≥ 3:1 | AA 级 |
| 焦点指示器 | ≥ 3:1 | AA 级 |

#### 键盘导航
- `Tab`：焦点在表单元素间移动
- `Shift + Tab`：反向焦点移动
- `Enter`：提交表单
- `Escape`：关闭模态框/取消操作
- `Space`：触发按钮/复选框

#### ARIA 标签
```html
<!-- 登录表单 -->
<form role="form" aria-label="登录表单">
  <input
    type="email"
    id="email"
    aria-required="true"
    aria-invalid="false"
    aria-describedby="email-hint"
  >
  <span id="email-hint">请输入您的邮箱地址</span>
</form>
```

### 7.2 焦点管理

#### 焦点陷阱（Modal）
```typescript
function useFocusTrap(containerRef: RefObject<HTMLElement>) {
  useEffect(() => {
    const container = containerRef.current;
    const focusableElements = container?.querySelectorAll(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    );

    const firstElement = focusableElements?.[0] as HTMLElement;
    const lastElement = focusableElements?.[
      focusableElements.length - 1
    ] as HTMLElement;

    const handleTab = (e: KeyboardEvent) => {
      if (e.key !== 'Tab') return;

      if (e.shiftKey) {
        if (document.activeElement === firstElement) {
          lastElement?.focus();
          e.preventDefault();
        }
      } else {
        if (document.activeElement === lastElement) {
          firstElement?.focus();
          e.preventDefault();
        }
      }
    };

    container?.addEventListener('keydown', handleTab);
    return () => container?.removeEventListener('keydown', handleTab);
  }, [containerRef]);
}
```

---

## 8. 性能优化

### 8.1 图片优化
```typescript
// Next.js Image 组件使用
<Image
  src="/logo.png"
  alt="App Logo"
  width={64}
  height={64}
  priority // 关键图片预加载
  sizes="64px"
/>
```

### 8.2 代码分割
```typescript
// 动态导入认证组件
const AuthModal = dynamic(() => import('./AuthModal'), {
  loading: () => <AuthSkeleton />,
  ssr: false // 客户端渲染
});
```

### 8.3 CSS 优化
```css
/* CSS containment 优化重绘性能 */
.login-card {
  contain: layout style paint;
}

/* GPU 加速动画 */
.animated-element {
  will-change: transform;
  transform: translateZ(0);
}
```

---

## 9. 浏览器兼容性

### 9.1 支持的浏览器版本
| 浏览器 | 最低版本 |
|--------|---------|
| Chrome | 90+ |
| Firefox | 88+ |
| Safari | 14+ |
| Edge | 90+ |
| iOS Safari | 14+ |
| Android Chrome | 90+ |

### 9.2 Polyfill 策略
```javascript
// core-js 特性检测
import 'core-js/actual/array/from';
import 'core-js/actual/string/starts-with';

// CSS Houdini 后备方案
@supports (backdrop-filter: blur(10px)) {
  .glass-effect {
    backdrop-filter: blur(10px);
  }
}
```

---

## 10. 技术栈建议

### 10.1 前端框架
- **React 18+**：组件化开发
- **Next.js 14+**：SSR/SSG 优化
- **TypeScript**：类型安全

### 10.2 UI 组件库
- **Radix UI**：无样式可访问组件
- **Tailwind CSS**：实用优先的 CSS 框架
- **Framer Motion**：动画库

### 10.3 状态管理
- **Zustand**：轻量级状态管理
- **React Query**：服务端状态管理

### 10.4 表单处理
- **React Hook Form**：表单验证
- **Zod**：Schema 验证

---

## 11. 组件 API 设计

### 11.1 AuthKit Provider
```typescript
import { AuthKitProvider } from '@workos/authkit-react';

function App() {
  return (
    <AuthKitProvider
      clientId="your_client_id"
      redirectUri={`${window.location.origin}/auth/callback`}
      theme={customTheme}
      onAuthSuccess={(user) => console.log(user)}
      onAuthError={(error) => console.error(error)}
    >
      <YourApp />
    </AuthKitProvider>
  );
}
```

### 11.2 登录按钮组件
```typescript
import { SignInButton } from '@workos/authkit-react';

function Header() {
  return (
    <SignInButton
      mode="modal"
      appearance="primary"
      className="custom-sign-in-btn"
    >
      Sign in
    </SignInButton>
  );
}
```

### 11.3 用户信息钩子
```typescript
import { useAuth } from '@workos/authkit-react';

function Profile() {
  const { user, isLoading, error } = useAuth();

  if (isLoading) return <ProfileSkeleton />;
  if (error) return <ErrorMessage />;

  return (
    <div>
      <img src={user.avatarUrl} alt={user.name} />
      <h2>{user.name}</h2>
      <p>{user.email}</p>
    </div>
  );
}
```

---

## 12. 设计交付物清单

### 12.1 设计文件
- [ ] Figma 设计源文件
- [ ] 组件设计规范文档
- [ ] 主题配置指南
- [ ] 图标资源包 (SVG)

### 12.2 开发资源
- [ ] Storybook 组件文档
- [ ] TypeScript 类型定义
- [ ] CSS 变量清单
- [ ] 动画演示视频

### 12.3 测试资源
- [ ] 视觉回归测试快照
- [ ] 可访问性测试报告
- [ ] 响应式测试截图
- [ ] 性能基准测试结果

---

## 附录：设计度量指标

| 指标 | 目标值 | 测量方法 |
|-----|--------|---------|
| 首次内容绘制 (FCP) | < 1.5s | Lighthouse |
| 最大内容绘制 (LCP) | < 2.5s | Lighthouse |
| 首次输入延迟 (FID) | < 100ms | Lighthouse |
| 累积布局偏移 (CLS) | < 0.1 | Lighthouse |
| 可访问性评分 | 100/100 | Lighthouse |
| SEO 评分 | 100/100 | Lighthouse |

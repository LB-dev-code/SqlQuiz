# FRONTEND USER-FRIENDLY DESIGN RULES
## Comprehensive Guidelines for SqlQuiz Platform

---

## 1. ACCESSIBILITY (A11Y) RULES

### Core Standards
- **WCAG 2.2 Compliance** (2026 best practice)
- **WCAG AAA Standard**: Contrast ratio 7:1 (highest level)

### Key Practices

#### Semantic HTML
```html
<!-- CORRECT -->
<button>Submit</button>
<nav>...</nav>
<main>...</main>

<!-- INCORRECT -->
<div onclick="submit()">Submit</div>
```

#### Keyboard Navigation
- All interactive elements must be keyboard accessible
- Logical tab order
- Visible focus states (focus outline)
- Support shortcuts (Esc to close, Enter to submit)

#### Image Alternative Text
- All images require meaningful alt descriptions
- Decorative images use `alt=""`
- Complex charts need detailed descriptions

#### ARIA Attributes
```html
<!-- Proper ARIA Usage -->
<button aria-label="Close dialog" aria-pressed="false">
  <span aria-hidden="true">×</span>
</button>

<div role="alert" aria-live="polite">Operation successful</div>
```

#### Detection Tools
- **Lighthouse** (Chrome built-in)
- **Axe DevTools**
- **pa11y** (CLI tool)
- **axe-cli** (command line)

---

## 2. FORM DESIGN BEST PRACTICES

### Core Principles
- **Simplicity First**: Request only essential information
- **Single Column Layout**: Superior to multi-column
- **Clear Communication**: Labels, hints, error messages must be clear

### Layout & Structure
```
RECOMMENDED Layout:
┌─────────────────────┐
│    Form Title       │
├─────────────────────┤
│  [Label]            │
│  [Input Field]      │
│  Helper Text        │
│  Error Message      │
└─────────────────────┘

AVOID multi-column layouts (poor mobile UX)
```

### Field Design Rules

#### Label Position
- **Labels above** input (superior to left-side)
- Avoid placeholder-only labels
- Use descriptive labels

#### Real-time Validation
- **Inline validation**: Immediate feedback
- Validate on blur
- Avoid showing all errors only on submit

#### Error Handling
```html
<!-- Good error message design -->
<div class="field-error">
  <span class="icon">⚠️</span>
  <span class="message">Password must be at least 8 characters</span>
</div>
```

### 2026 Trends

#### AI-Driven Personalization
- Adaptive forms (change based on user behavior)
- Intelligent input suggestions
- Contextual keyboards

#### Progressive Enhancement
```html
<!-- HTML works first -->
<form action="/submit" method="POST">
  <!-- JavaScript enhancement -->
  <script>
    // Enhanced features
  </script>
</form>
```

#### Input Mode Optimization
```html
<!-- Mobile optimization -->
<input type="email" inputmode="email" autocomplete="email">
<input type="tel" inputmode="tel" autocomplete="tel">
<input type="numeric" inputmode="decimal">
```

---

## 3. ERROR HANDLING & USER FEEDBACK RULES

### Design Philosophy
> "The best error message is the one the user never sees"

### Error Prevention First

#### Prevention Strategies
1. **Smart defaults**
2. **Input constraints** (maxlength, pattern)
3. **Real-time validation**
4. **Clear instructions**

### Error Message Design

#### Characteristics
- **Clear**: User can understand
- **Actionable**: Tell user how to fix
- **Non-technical**: Avoid code terminology
- **Well-positioned**: Close to error source

#### Example Comparison
```
BAD: Technical error
"Error 500: Internal Server Error"

GOOD: User-friendly error
"Sorry, the system encountered a problem. Please try again later, or contact support."

BAD: Vague error
"Invalid input"

GOOD: Specific error
"Email format is incorrect, must include @ symbol"
```

### Feedback Levels

#### Success Feedback
```css
.success-message {
  background: #d4edda;
  color: #155724;
  border-left: 4px solid #28a745;
  padding: 12px 16px;
  border-radius: 4px;
}
```

#### Warning Feedback
```css
.warning-message {
  background: #fff3cd;
  color: #856404;
  border-left: 4px solid #ffc107;
}
```

#### Error Feedback
```css
.error-message {
  background: #f8d7da;
  color: #721c24;
  border-left: 4px solid #dc3545;
}
```

### 2026 Trends

#### AI-Driven Error Recovery
- Natural language error explanations
- Auto-fix suggestions
- Contextual help

#### Multimodal Feedback
- Visual (color, icons)
- Tactile (vibration feedback)
- Audio (sound cues)

---

## 4. LOADING STATES & PROGRESS INDICATORS

### Core Principles
- **Keep user informed**
- **Enhance perceived performance**
- **Reduce waiting anxiety**

### Loader Types

#### 1. Spinners
```css
.spinner {
  border: 3px solid #f3f3f3;
  border-top: 3px solid #3498db;
  border-radius: 50%;
  width: 40px;
  height: 40px;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
```

#### 2. Progress Bars
```html
<div class="progress-container">
  <div class="progress-bar" style="width: 45%"></div>
  <span class="progress-text">45%</span>
</div>
```

#### 3. Skeleton Screens
```html
<!-- Show skeleton before content loads -->
<div class="skeleton-card">
  <div class="skeleton-avatar"></div>
  <div class="skeleton-title"></div>
  <div class="skeleton-text"></div>
</div>
```

#### 4. Ghost Buttons
```html
<button class="btn-loading" disabled>
  <span class="spinner"></span>
  Submitting...
</button>
```

### Best Practices

#### Time Thresholds
| Wait Time | Strategy |
|-----------|----------|
| < 0.1s | User feels instant response |
| 0.1-1s | Smooth experience, no feedback needed |
| 1-5s | Show loading indicator |
| > 5s | Show progress bar + estimated time |

#### Optimize Perceived Performance
- **Progressive loading**: Priority content first
- **Optimistic UI**: Show result first, submit in background
- **Skeleton screens**: Better than spinner (shows what's coming)

---

## 5. RESPONSIVE DESIGN RULES

### Core Strategy
- **Mobile-First**: Design starting from small screens
- **Progressive Enhancement**: Basic functionality first
- **Flexible layouts**: Adapt to different screens

### Breakpoint Standards (2026)

```css
/* Mobile-first breakpoints */
:root {
  --bp-xs: 375px;   /* Small phones */
  --bp-sm: 640px;   /* Large phones */
  --bp-md: 768px;   /* Tablet portrait */
  --bp-lg: 1024px;  /* Tablet landscape/small laptop */
  --bp-xl: 1280px;  /* Desktop */
  --bp-2xl: 1536px; /* Large screens */
}

/* Usage example */
.container {
  padding: 1rem;
}

@media (min-width: 768px) {
  .container {
    padding: 2rem;
  }
}
```

### Layout Techniques

#### Flexbox
```css
.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap; /* Wrap on mobile */
}
```

#### CSS Grid
```css
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1.5rem;
}
```

#### Container Queries (2026 recommended)
```css
.card {
  container-type: inline-size;
}

@container (min-width: 400px) {
  .card-title {
    font-size: 1.5rem;
  }
}
```

### Fluid Design Principles

#### Relative Units
```css
/* Use relative units */
.content {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 clamp(1rem, 5%, 3rem);
}
```

#### Fluid Typography
```css
/* Use clamp() for font range */
h1 {
  font-size: clamp(1.5rem, 4vw, 3rem);
}
```

#### Fluid Spacing
```css
.section {
  padding: clamp(2rem, 5vh, 6rem) 0;
}
```

### Responsive Images

```html
<!-- Responsive images -->
<img
  src="small.jpg"
  srcset="small.jpg 400w,
          medium.jpg 800w,
          large.jpg 1200w"
  sizes="(max-width: 600px) 400px,
         (max-width: 1200px) 800px,
         1200px"
  alt="Description"
  loading="lazy"
>
```

---

## 6. COLOR CONTRAST & VISUAL HIERARCHY

### WCAG 2.2 Contrast Standards

| Text Type | Min Contrast (AA) | AAA Level |
|-----------|-------------------|-----------|
| Body text | 4.5:1 | 7:1 |
| Large text (18pt+) | 3:1 | 4.5:1 |
| Icons/graphics | 3:1 | - |

### 2026 New Standards
- **WCAG 3.0/APCA** (Accessible Perceptual Contrast Algorithm)
- Evolution from traditional WCAG 2.x
- More accurate perceptual contrast calculation

### Contrast Detection Tools
- **Figma plugins**
- **Adobe Color**
- **WebAIM Contrast Checker**
- **Test on real components** (not just color swatches)

### Visual Hierarchy Principles

#### Size Hierarchy
```css
/* Heading hierarchy */
h1 { font-size: clamp(2rem, 5vw, 3.5rem); font-weight: 700; }
h2 { font-size: clamp(1.5rem, 4vw, 2.5rem); font-weight: 600; }
h3 { font-size: clamp(1.25rem, 3vw, 2rem); font-weight: 600; }
```

#### Color Hierarchy
```css
/* Clear primary/secondary */
--color-primary: #2563eb;
--color-secondary: #64748b;
--color-muted: #94a3b8;
--color-border: #e2e8f0;
```

#### Spacing Hierarchy
```css
/* 8px baseline spacing */
--space-1: 0.5rem;   /* 8px */
--space-2: 1rem;     /* 16px */
--space-3: 1.5rem;   /* 24px */
--space-4: 2rem;     /* 32px */
--space-6: 3rem;     /* 48px */
```

### Color Accessibility

#### Don't Rely on Color Alone
```css
/* BAD: Only color for state */
.error { color: red; }
.success { color: green; }

/* GOOD: Color + icon + border */
.error {
  color: #dc3545;
  border-left: 4px solid #dc3545;
}
.error::before {
  content: "⚠️";
  margin-right: 0.5rem;
}
```

#### Dark Mode Support
```css
@media (prefers-color-scheme: dark) {
  :root {
    --bg-primary: #1a1a1a;
    --text-primary: #f5f5f5;
    --color-primary: #60a5fa;
  }
}
```

---

## 7. INTERACTION FEEDBACK RULES

### Core Principles
- **Immediacy**: Feedback happens instantly
- **Clarity**: User knows exactly what happened
- **Fluidity**: Doesn't interrupt user flow

### Micro-interaction Types

#### 1. Button Feedback
```css
.button {
  transition: all 0.2s ease;
}

.button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.button:active {
  transform: translateY(0);
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}
```

#### 2. Form Input Feedback
```css
.input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.input:valid {
  border-color: #22c55e;
}

.input:invalid {
  border-color: #ef4444;
}
```

#### 3. Loading State Feedback
```css
.button.loading {
  position: relative;
  color: transparent;
}

.button.loading::after {
  content: "";
  position: absolute;
  width: 16px;
  height: 16px;
  border: 2px solid #fff;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}
```

#### 4. Drag Feedback
```css
.draggable {
  cursor: grab;
  transition: transform 0.2s, box-shadow 0.2s;
}

.draggable:active {
  cursor: grabbing;
}

.draggable.dragging {
  transform: scale(1.05);
  box-shadow: 0 10px 30px rgba(0,0,0,0.2);
  opacity: 0.9;
}
```

### Feedback Timing

| Action Type | Feedback Timing |
|-------------|----------------|
| Hover | < 100ms |
| Click | Instant visual change |
| Loading | < 200ms show indicator |
| Submit | Instant disable button + loading state |
| Success | Clear success message |

---

## 8. MOBILE TOUCH-FRIENDLY RULES

### Touch Target Size

#### Minimum Size Standards
| Element Type | Minimum Size | Recommended Size |
|--------------|--------------|------------------|
| Buttons/Links | 44×44px | 48×48px+ |
| Input fields | 44×44px | 48×48px+ |
| Checkboxes | 44×44px | 48×48px+ |
| Icon buttons | 44×44px | 48×48px+ |

#### Finger Size Reference
- Average fingertip touch: **1.6-2cm (45-57 pixels)**
- Average thumb width: **2.2cm (75 pixels)**
- Minimum spacing: **8 pixels**

```css
/* Touch-friendly button */
.touch-button {
  min-height: 48px;
  min-width: 48px;
  padding: 12px 24px;
  margin: 8px;
  font-size: 16px; /* Prevent iOS auto-zoom */
}
```

### Touch Spacing

```css
/* Touch target spacing */
.nav-item {
  margin: 8px; /* At least 8px spacing */
  min-width: 48px;
  min-height: 48px;
}

/* Prevent touch target overlap */
.button-group button {
  margin: 4px 8px;
}
```

### Gesture Zones

```css
/* Bottom navigation (easy to reach) */
.bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 64px;
  padding: env(safe-area-inset-bottom);
}
```

### Text Size

```css
/* Prevent iOS auto-zoom */
input, textarea, select {
  font-size: 16px; /* At least 16px */
}

/* Readable body text */
body {
  font-size: 16px;
  line-height: 1.5;
}
```

### Touch Optimization

#### Disable Double-tap Zoom
```html
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
```

#### Optimize Touch Delay
```css
/* Remove 300ms delay */
* {
  touch-action: manipulation;
}
```

#### Gesture Conflict Handling
```javascript
// Distinguish swipe from tap
let startX, startY;
element.addEventListener('touchstart', (e) => {
  startX = e.touches[0].clientX;
  startY = e.touches[0].clientY;
});

element.addEventListener('touchend', (e) => {
  const endX = e.changedTouches[0].clientX;
  const endY = e.changedTouches[0].clientY;

  if (Math.abs(endX - startX) < 10 && Math.abs(endY - startY) < 10) {
    // Is tap, not swipe
    handleClick();
  }
});
```

### Safe Area Adaptation (Notch Screens)

```css
/* iOS safe area */
.header {
  padding-top: env(safe-area-inset-top);
}

.footer {
  padding-bottom: env(safe-area-inset-bottom);
}

.sidebar {
  padding-left: env(safe-area-inset-left);
  padding-right: env(safe-area-inset-right);
}
```

---

## 9. DESIGN SYSTEM RULES

### Component Standards

#### Button Variants
```css
/* Primary button - for main actions */
.btn-primary {
  background: var(--magma-core);
  color: white;
  font-weight: 600;
  min-height: 48px;
}

/* Secondary button - for alternative actions */
.btn-secondary {
  background: transparent;
  border: 2px solid var(--magma-core);
  color: var(--magma-core);
}

/* Ghost button - for tertiary actions */
.btn-ghost {
  background: transparent;
  color: var(--magma-core);
}
```

#### Input Standards
```css
.input {
  min-height: 48px;
  padding: 12px 16px;
  border: none;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.3);
  color: var(--text-primary);
}

.input:focus {
  outline: none;
  box-shadow: 0 0 0 3px rgba(255, 69, 0, 0.2);
}
```

### Spacing System

```css
:root {
  /* Spacing scale - 8px base unit */
  --space-1: 4px;    /* 0.25rem */
  --space-2: 8px;    /* 0.5rem */
  --space-3: 12px;   /* 0.75rem */
  --space-4: 16px;   /* 1rem */
  --space-5: 20px;   /* 1.25rem */
  --space-6: 24px;   /* 1.5rem */
  --space-8: 32px;   /* 2rem */
  --space-10: 40px;  /* 2.5rem */
  --space-12: 48px;  /* 3rem */
  --space-16: 64px;  /* 4rem */
}
```

### Typography Scale

```css
:root {
  /* Type scale */
  --text-xs: 0.75rem;    /* 12px */
  --text-sm: 0.875rem;   /* 14px */
  --text-base: 1rem;     /* 16px */
  --text-lg: 1.125rem;   /* 18px */
  --text-xl: 1.25rem;    /* 20px */
  --text-2xl: 1.5rem;    /* 24px */
  --text-3xl: 1.875rem;  /* 30px */
  --text-4xl: 2.25rem;   /* 36px */
  --text-5xl: 3rem;      /* 48px */
}
```

### Border Radius

```css
:root {
  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --radius-xl: 16px;
  --radius-2xl: 24px;
  --radius-full: 9999px;
}
```

---

## 10. PERFORMANCE RULES

### Loading Performance

#### Critical Rendering Path
```html
<!-- Inline critical CSS -->
<style>
  /* Critical above-fold styles */
</style>

<!-- Defer non-critical CSS -->
<link rel="preload" href="styles.css" as="style" onload="this.onload=null;this.rel='stylesheet'">
```

#### Image Optimization
```html
<!-- Lazy load images -->
<img src="image.jpg" loading="lazy" alt="Description">

<!-- Use modern formats -->
<picture>
  <source srcset="image.avif" type="image/avif">
  <source srcset="image.webp" type="image/webp">
  <img src="image.jpg" alt="Description">
</picture>
```

### Runtime Performance

#### Animation Performance
```css
/* Use transform and opacity for animations */
.animated {
  /* GOOD: GPU accelerated */
  transform: translateX(100px);
  opacity: 0.5;

  /* BAD: Triggers layout */
  /* width: 100px; */
  /* left: 100px; */
}
```

#### Debounce & Throttle
```javascript
// Debounce for search input
function debounce(func, wait) {
  let timeout;
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout);
      func(...args);
    };
    clearTimeout(timeout);
    timeout = setTimeout(later, wait);
  };
}

// Throttle for scroll events
function throttle(func, limit) {
  let inThrottle;
  return function(...args) {
    if (!inThrottle) {
      func.apply(this, args);
      inThrottle = true;
      setTimeout(() => inThrottle = false, limit);
    }
  };
}
```

---

## 11. CONTENT RULES

### Writing Guidelines

#### Error Messages
- **Be specific**: What went wrong?
- **Be actionable**: How to fix it?
- **Be human**: Avoid technical jargon
- **Be concise**: Get to the point

#### Button Labels
- **Use verbs**: "Submit", "Cancel", "Delete"
- **Be specific**: "Save changes" not "OK"
- **Indicate risk**: "Delete permanently" not "Delete"

#### Help Text
- **Explain why**: Why is this field required?
- **Show format**: What format is expected?
- **Give examples**: Show a valid example

### Empty States

```html
<!-- Good empty state -->
<div class="empty-state">
  <img src="empty-illustration.svg" alt="No quizzes yet">
  <h3>No quizzes found</h3>
  <p>Create your first quiz to get started.</p>
  <button>Create Quiz</button>
</div>
```

---

## 12. TESTING RULES

### Cross-Browser Testing

#### Target Browsers
- **Chrome/Edge** (latest + 2 versions)
- **Firefox** (latest + 2 versions)
- **Safari** (latest + 2 versions)
- **Mobile Safari** (iOS 14+)
- **Chrome Mobile** (Android 10+)

### Device Testing

#### Essential Devices
- iPhone (12/13/14 Pro)
- Samsung Galaxy S21+
- iPad (Air/Pro)
- Desktop (1920×1080)
- Laptop (1366×768)

### Accessibility Testing

#### Automated Tools
```bash
# Lighthouse CI
npx lighthouse http://localhost:3000 --view

# Axe CLI
npx axe http://localhost:3000

# pa11y
npx pa11y http://localhost:3000
```

#### Manual Testing
- Navigate with keyboard only (Tab, Enter, Esc, Arrow keys)
- Test with screen reader (VoiceOver/NVDA)
- Test with browser zoom (200%)
- Test high contrast mode

---

## PRE-RELEASE CHECKLIST

### Design
- [ ] All interactive elements keyboard accessible
- [ ] Color contrast meets WCAG AA (4.5:1)
- [ ] Touch targets at least 44×44px
- [ ] Forms have clear labels and error hints
- [ ] Loading states have clear indicators
- [ ] Tested responsive layout on multiple devices
- [ ] All images have alt text
- [ ] Focus states are visible
- [ ] Error messages are user-friendly and actionable
- [ ] Passed Lighthouse accessibility audit

### Performance
- [ ] Lighthouse Performance score > 90
- [ ] First Contentful Paint < 1.8s
- [ ] Time to Interactive < 3.8s
- [ ] Cumulative Layout Shift < 0.1
- [ ] Images optimized and lazy-loaded
- [ ] CSS minified and critical
- [ ] JavaScript minified and deferred

### Code Quality
- [ ] No console errors or warnings
- [ ] Semantic HTML used throughout
- [ ] ARIA attributes correct and necessary
- [ ] Form validation works without JavaScript
- [ ] Graceful degradation for older browsers

---

## GLASSMORPHISM DESIGN RULES

### Core Principles for SqlQuiz

#### NO Border Strokes
```css
/* BAD: Uses borders */
.glass {
  border: 1px solid rgba(255,255,255,0.2);
}

/* GOOD: Uses shadows only */
.glass {
  box-shadow:
    0 1px 3px rgba(62, 60, 56, 0.04),
    0 8px 24px rgba(62, 60, 56, 0.06),
    inset 0 1px 0 rgba(255, 255, 255, 0.4);
}
```

#### NO Blue/Purple/Pink AI Colors
- Use earth tones, terracotta, sage, camel palette
- Forbidden: #0066FF, #8B5CF6, #EC4899
- Allowed: #C37A67, #9CAF88, #C19A6B

#### NO Symmetric Card Stacking
```css
/* BAD: Perfectly centered, symmetric */
.card {
  margin: 0 auto;
  text-align: center;
}

/* GOOD: Asymmetric, interesting layout */
.card:nth-child(1) {
  margin-left: 0;
  margin-right: 12%;
}

.card:nth-child(2) {
  margin-left: 12%;
  margin-right: 0;
}
```

#### NO Subtle Gradient Bokeh Orbs
```css
/* BAD: Generic bokeh */
.background {
  background-image: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 50%);
}

/* GOOD: Purposeful, minimal gradients */
.background {
  background-image:
    radial-gradient(ellipse at 15% 25%, rgba(195, 122, 103, 0.06) 0%, transparent 50%),
    radial-gradient(ellipse at 88% 72%, rgba(156, 175, 136, 0.05) 0%, transparent 50%);
}
```

#### NO Standard Body Text 16px/1.5rem
```css
/* BAD: Generic body text */
body {
  font-size: 16px;
  line-height: 1.5;
}

/* GOOD: Intentional sizing */
body {
  font-size: 15px;
  line-height: 1.6;
  letter-spacing: 0.01em;
}
```

#### NO Inter/Geist Font Family
```css
/* BAD: Overused fonts */
font-family: 'Inter', sans-serif;
font-family: 'Geist', sans-serif;

/* GOOD: Unique, personality fonts */
font-family: 'Noto Serif SC', 'Source Han Serif SC', serif;
font-family: 'Oswald', 'Impact', sans-serif;
font-family: 'JetBrains Mono', monospace;
```

#### NO 0.1s Staggered Animations
```css
/* BAD: Predictable stagger */
.item:nth-child(1) { animation-delay: 0.1s; }
.item:nth-child(2) { animation-delay: 0.2s; }
.item:nth-child(3) { animation-delay: 0.3s; }

/* GOOD: Spring physics, varied timing */
.item:nth-child(1) { animation-delay: 0.05s; }
.item:nth-child(2) { animation-delay: 0.14s; }
.item:nth-child(3) { animation-delay: 0.26s; }
```

#### NO scale(1.02) Hover Effects
```css
/* BAD: Generic scale */
.button:hover {
  transform: scale(1.02);
}

/* GOOD: Purposeful motion */
.button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(195, 122, 103, 0.32);
}
```

#### NO 50:50 Card Splits
```css
/* BAD: Even split */
.container {
  display: grid;
  grid-template-columns: 1fr 1fr;
}

/* GOOD: Interesting proportions */
.container {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
}

/* OR 55:45 split */
.container {
  display: grid;
  grid-template-columns: 1.04fr 0.96fr;
}
```

#### NO 1.2rem Horizontal Padding, 3rem Vertical Gap
```css
/* BAD: Predictable spacing */
.card {
  padding: 0 1.2rem;
  gap: 3rem;
}

/* GOOD: Intentional spacing */
.card {
  padding: 0 1.25rem;
  gap: 2.8rem;
}

/* OR irregular spacing */
.card {
  padding: var(--space-md);
  padding-inline: var(--space-xl);
}
```

### ALLOWED Patterns

#### Serif + Mono Typography Mix
```css
.headings {
  font-family: 'Noto Serif SC', serif;
}

.body {
  font-family: 'Crimson Pro', serif;
}

.meta {
  font-family: 'JetBrains Mono', monospace;
}

.ui {
  font-family: -apple-system, BlinkMacSystemFont, sans-serif;
}
```

#### Spring-Physics Animations
```css
:root {
  --ease-spring: cubic-bezier(0.34, 1.56, 0.64, 1);
  --ease-smooth: cubic-bezier(0.4, 0, 0.2, 1);
}

.animate-enter {
  animation: enter 0.6s var(--ease-spring);
}

@keyframes enter {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
```

#### Subtle Gradient Glows
```css
/* Minimal, purposeful glows */
.brand-badge {
  background: rgba(195, 122, 103, 0.1);
  box-shadow: 0 0 20px rgba(195, 122, 103, 0.15);
}

/* NOT: Large bokeh orbs */
```

---

## VOLCANO ERUPTION THEME RULES

### Color Palette

#### Magma Colors
```css
--magma-core: #FF2200;
--magma-primary: #FF3D2B;
--magma-secondary: #FF5722;
--magma-glow: #FF6B35;
--magma-ember: #FF8C42;
```

#### Obsidian Colors
```css
--obsidian-deep: #0A0808;
--obsidian-base: #1A1414;
--obsidian-mid: #2D2222;
--obsidian-light: #4A3535;
```

#### Volcanic Ash
```css
--ash-dark: #6B5A5A;
--ash-mid: #8B7A7A;
--ash-light: #AB9A9A;
```

#### Molten Gold
```css
--gold-melt: #FFB300;
--gold-flow: #FFC107;
--gold-cool: #FFD54F;
```

### Typography

#### Display Font
```css
--font-display: 'Oswald', 'Impact', sans-serif;
```

#### Serif Font
```css
--font-serif: 'Noto Serif SC', serif;
```

#### Mono Font
```css
--font-mono: 'JetBrains Mono', monospace;
```

### Shadow System

#### Magma Shadows
```css
--shadow-magma-sm:
  0 2px 8px rgba(255, 34, 0, 0.15),
  0 8px 24px rgba(255, 34, 0, 0.1),
  inset 0 1px 0 rgba(255, 200, 150, 0.15);

--shadow-magma-md:
  0 4px 16px rgba(255, 34, 0, 0.2),
  0 16px 48px rgba(255, 34, 0, 0.15),
  inset 0 1px 0 rgba(255, 200, 150, 0.2);

--shadow-magma-lg:
  0 8px 32px rgba(255, 34, 0, 0.25),
  0 32px 80px rgba(255, 34, 0, 0.2),
  inset 0 1px 0 rgba(255, 200, 150, 0.25);
```

#### Obsidian Shadows
```css
--shadow-obsidian:
  0 4px 16px rgba(0, 0, 0, 0.4),
  0 16px 48px rgba(0, 0, 0, 0.3),
  inset 0 1px 0 rgba(255, 100, 50, 0.1);
```

#### Glow Effects
```css
--shadow-glow:
  0 0 20px rgba(255, 61, 43, 0.3),
  0 0 40px rgba(255, 61, 43, 0.15),
  0 0 60px rgba(255, 61, 43, 0.08);
```

### Animations

#### Magma Pulse
```css
@keyframes magmaPulse {
  0%, 100% {
    box-shadow:
      0 0 20px rgba(255, 61, 43, 0.3),
      inset 0 1px 0 rgba(255, 150, 100, 0.2);
  }
  50% {
    box-shadow:
      0 0 30px rgba(255, 61, 43, 0.5),
      inset 0 1px 0 rgba(255, 150, 100, 0.3);
  }
}
```

#### Lava Flow
```css
@keyframes magmaFlow {
  0% { background-position: -200% center; }
  100% { background-position: 200% center; }
}
```

#### Emerge from Magma
```css
@keyframes emergeFromMagma {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
```

---

## CONTENT GUIDELINES

### Writing Style
- **Be concise**: Get to the point quickly
- **Use active voice**: "Submit your quiz" not "Your quiz should be submitted"
- **Avoid jargon**: Use plain language
- **Be specific**: "Enter your email address" not "Enter input"

### Examples
```
GOOD: "Create a new SQL quiz"
GOOD: "Enter your password (at least 8 characters)"
GOOD: "Your quiz has been saved"

BAD: "Quiz creation"
BAD: "Password"
BAD: "Success"
```

---

## IMPORTANT NOTICE

### ENGLISH-ONLY SYSTEM

This is an **English-only system**. The following rules apply:

1. **NO Chinese characters** in any user-facing content
2. **NO mixed-language** interfaces (English with Chinese)
3. **NO translated fallbacks** - English content must be complete
4. **ALL UI text** must be in English
5. **ALL error messages** must be in English
6. **ALL labels, placeholders, help text** must be in English

### Examples

```
✓ CORRECT:
<button>Submit Quiz</button>
<label>Email Address</label>
<p>Your quiz has been created successfully.</p>

✗ INCORRECT:
<button>提交测验</button>
<label>邮箱地址</label>
<p>Your quiz has been created 成功.</p>
```

### Code Comments
- Code comments may use any language for documentation
- User-facing strings MUST be English only

### Testing Requirement
- All user-facing text must be reviewed for English correctness
- Spelling and grammar must be verified
- Cultural appropriateness for English-speaking audiences

---

## FINAL REMINDERS

### Design Philosophy
2026 frontend user-friendly design core themes: **Inclusivity, Intelligence, Performance**

1. **Accessibility First**: Consider all users from design start
2. **AI Enhanced**: Personalized experience and intelligent assistance
3. **Instant Feedback**: Micro-interactions build user confidence
4. **Mobile First**: Touch-friendly interaction design
5. **Performance Aware**: Maintain smooth feel even during loading

### Remember
**UX Design is a living system** - evolve from real user behavior and feedback, not static rules.

---

*Last Updated: 2026-01-31*
*Version: 2.0*
*Maintained by: Frontend Team*

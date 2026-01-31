---
name: glassmorphism_design
description: A high-end, anti-mediocre guide to crafting distinct glassmorphism interfaces, rejecting common trends.
---

# Glassmorphism Design: The Anti-Mediocre Standard

> "We do not build plastic dashboards. We build refractive digital artifacts."

## 1. The Strict "Forbidden" List (Non-Negotiable)
Per the Lead Designer's strict visual code, the following are **BANNED**:

1.  **NO "Blurple" AI Gradients**: No generic blue/purple/pink linear gradients. Use earth tones, monochromatic noise, or caustic lighting.
2.  **NO Stroked 1px Borders**: The white 1px border is lazy. Use **Inset Shadows** and **Caustic Edges** to define geometry.
3.  **NO Symmetrical Stacking**: Do not stack cards in the center. Use asymmetry, masonry, and overlapping planes.
4.  **NO Bokeh Orbs**: No floating, blurry circles. Use sophisticated **Mesh Gradients** or **Atmospheric Noise**.
5.  **NO "Textbook" Typography**: `16px` / `1.5rem` is forbidden. It looks generic. Use **Fluid Type**, massive headers, or tiny tech-mono metadata.
6.  **NO Geometric Sans Defaults**: `Inter`, `Geist`, `Satoshi` are banned as primary fonts. Use **Editorial Serif** (e.g., Playfair, Canela) or **Brutalist Mono**.
7.  **NO Fixed Layout Formulas**: [Nav -> Hero -> Cards] is dead. Use broken grids and deep Z-axis exploration.
8.  **NO Cliche Animations**: No staggered fade-ins. Use distinct, non-linear motion.
9.  **NO AI Icons**: Use custom semantic SVGs or CSS shapes.
10. **NO Cultural Vacuum**: The design *must* have a specific character (e.g., "Kyoto Mist", "Berlin Concrete", "NYC Penthouse").

## 2. The Core Visual DNA (CSS)

### The Material (Not just blur)
True glassmorphism is about *refraction* and *imperfection*.

```css
:root {
  --glass-surface: rgba(255, 255, 255, 0.02);
  --glass-border: rgba(255, 255, 255, 0.08);
  --glass-shadow: 0 24px 48px -12px rgba(0, 0, 0, 0.5);
  --noise-texture: url("data:image/svg+xml,..."); /* Add SVG noise here */
}

/* The High-End Panel */
.glass-panel {
  background: var(--glass-surface);
  backdrop-filter: blur(20px) saturate(180%) brightness(1.1);
  -webkit-backdrop-filter: blur(20px) saturate(180%) brightness(1.1);
  
  /* The "Caustic" Edge (Replaces Border) */
  box-shadow: 
    inset 0 1px 0 0 rgba(255, 255, 255, 0.3), /* Top Highlight */
    inset 0 -1px 0 0 rgba(0, 0, 0, 0.2),      /* Bottom Shadow */
    var(--glass-shadow);
  
  /* No default radius */
  border-radius: 0; 
}
```

### Typography (Anti-Textbook)
Break the rhythm.

```css
/* Editorial Header */
h1 {
  font-family: 'Playfair Display', serif; /* Or a jagged custom font */
  font-size: clamp(3rem, 10vw, 8rem);
  line-height: 0.9;
  letter-spacing: -0.04em;
  color: rgba(255, 255, 255, 0.9);
  mix-blend-mode: overlay;
}

/* Technical Metadata */
.meta-text {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.75rem; /* Tiny */
  text-transform: uppercase;
  letter-spacing: 0.2em;
  opacity: 0.6;
}

/* Body Text - Intentionally dense or loose, never standard */
p {
  font-size: 1.125rem; /* 18px */
  line-height: 1.4;
  /* OR */
  font-size: 0.875rem; /* 14px */
  line-height: 1.8;
}
```

## 3. Workflow & Layout

1.  **Define the Atmosphere**: Choose a "Location" (e.g., "Rainy Tokyo").
2.  **Build the Light**: Do not just place a background color. Build a complex CSS gradient or use an abstract image.
3.  **Place the Artifacts**: Glass panels should overlap. One should be "in front" (clearer), one "behind" (blurrier).
4.  **Inject Noise**: Every glass surface *must* have a subtle noise overlay to prevent "plastic" feel.

## 4. References & Inspiration
*   [Themesberg Tutorial](https://themesberg.com/blog/glassmorphism/tutorial?ref=glass-ui-generator)
*   [Hyper-Liquid Color Gen](https://freefrontend.com/code/glassmorphic-hsl-color-palette-generator-2026-01-23/)
*   [Advanced CSS Glass](https://freefrontend.com/css-glassmorphism/#google_vignette)

## 5. Implementation Strategy
When asking the AI to implement this:
1.  **Ask for the "Theme Name"** (e.g., "I want 'Obsidian Glass'").
2.  **Provide the exact text content** to ensure typography shapes the layout.
3.  **Reject any output** that looks like a Bootstrap card with `backdrop-filter`.

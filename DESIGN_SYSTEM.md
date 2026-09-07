# ProjectFlow Design System

> Reference document for the ProjectFlow visual language. Use this when creating new UI elements, templates, or components to ensure consistency.

---

## 1. Color Palette

All colors are defined as CSS custom properties in `src/main/resources/static/css/app.css` under `:root`.

### Brand & Accent

| Token                  | Value     | Usage                          |
| ---------------------- | --------- | ------------------------------ |
| `--pf-primary`         | `#3b82f6` | Primary actions, links, focus  |
| `--pf-primary-hover`   | `#2563eb` | Hover state of primary         |
| `--pf-primary-active`  | `#1d4ed8` | Active / pressed state         |
| `--pf-primary-soft`    | `#eff6ff` | Light tint backgrounds         |
| `--pf-primary-border`  | `#bfdbfe` | Borders on primary-tinted elements |

### Neutrals & Surfaces

| Token                  | Value     | Usage                          |
| ---------------------- | --------- | ------------------------------ |
| `--pf-bg`              | `#f8fafc` | Page background                |
| `--pf-surface`         | `#ffffff` | Card / panel backgrounds       |
| `--pf-surface-raised`  | `#ffffff` | Elevated surfaces              |
| `--pf-surface-subtle`  | `#f1f5f9` | Muted backgrounds (inputs, tabs) |
| `--pf-border`          | `#e2e8f0` | Default border color           |
| `--pf-border-strong`   | `#cbd5e1` | Input borders                  |
| `--pf-border-hover`    | `#94a3b8` | Hover border emphasis          |

### Text

| Token                  | Value     | Usage                          |
| ---------------------- | --------- | ------------------------------ |
| `--pf-text-main`       | `#0f172a` | Headings, primary text         |
| `--pf-text-secondary`  | `#475569` | Body text, descriptions        |
| `--pf-text-muted`      | `#64748b` | Labels, captions, hints        |
| `--pf-text-light`      | `#94a3b8` | Placeholders                   |
| `--pf-text-inverse`    | `#ffffff` | Text on dark/primary bg        |

### Semantic Status

| Category | Background       | Border             | Text               | Accent    |
| -------- | ---------------- | ------------------- | ------------------- | --------- |
| Success  | `--pf-success-bg` (`#ecfdf5`) | `--pf-success-border` (`#a7f3d0`) | `--pf-success-text` (`#065f46`) | `--pf-success` (`#10b981`) |
| Warning  | `--pf-warning-bg` (`#fffbeb`) | `--pf-warning-border` (`#fde68a`) | `--pf-warning-text` (`#92400e`) | `--pf-warning` (`#f59e0b`) |
| Danger   | `--pf-danger-bg` (`#fef2f2`)  | `--pf-danger-border` (`#fecaca`) | `--pf-danger-text` (`#991b1b`)  | `--pf-danger` (`#ef4444`)  |
| Purple   | `--pf-purple-bg` (`#f5f3ff`)  | `--pf-purple-border` (`#ddd6fe`) | `--pf-purple-text` (`#5b21b6`)  | `--pf-purple` (`#8b5cf6`)  |

---

## 2. Typography

### Font Families

| Token           | Stack                                                        | Usage           |
| --------------- | ------------------------------------------------------------ | --------------- |
| Display font    | `'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif` | Headings (h1–h6) |
| Body font       | `'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif` | Body text, UI |

### Font Sizes & Weights

| Element | Size                        | Weight | Letter-spacing |
| ------- | --------------------------- | ------ | -------------- |
| `h1`    | `clamp(1.85rem, 3.5vw, 2.35rem)` | 800    | `-0.035em`     |
| `h2`    | `1.35rem`                   | 700    | `-0.025em`     |
| `h3`    | `1.1rem`                    | 700    | `-0.025em`     |
| Body    | `0.95rem`                   | 400    | normal         |
| Small   | `0.82rem–0.88rem`           | 500–600 | normal        |
| Caption | `0.75rem–0.8rem`            | 600–700 | `0.02–0.04em`  |

---

## 3. Spacing & Radii

### Border Radii

| Token              | Value    | Usage                    |
| ------------------ | -------- | ------------------------ |
| `--pf-radius-sm`   | `6px`    | Small inputs, pills      |
| `--pf-radius-md`   | `10px`   | Buttons, inputs, icons   |
| `--pf-radius-lg`   | `14px`   | Cards, panels            |
| `--pf-radius-xl`   | `20px`   | Auth cards, CTA banners  |
| `--pf-radius-full` | `9999px` | Circular elements, badges |

### Elevation & Shadows

| Token              | Usage                      |
| ------------------ | -------------------------- |
| `--pf-shadow-xs`   | Subtle, inputs             |
| `--pf-shadow-sm`   | Cards at rest              |
| `--pf-shadow-md`   | Hover states               |
| `--pf-shadow-lg`   | Elevated panels, modals    |
| `--pf-shadow-xl`   | Dropdowns, popovers        |
| `--pf-shadow-glow` | Primary accent glow        |

---

## 4. Component Catalog

### Buttons

Base class: `.pf-btn` (also auto-applied to `button` and `input[type="submit"]`)

| Modifier             | Appearance                          |
| -------------------- | ----------------------------------- |
| `.pf-btn--primary`   | Blue filled (default for `.pf-btn`) |
| `.pf-btn--secondary` | White with border                   |
| `.pf-btn--outline`   | Transparent with primary border     |
| `.pf-btn--ghost`     | No background, no border            |
| `.pf-btn--danger`    | Red filled                          |
| `.pf-btn--danger-outline` | Transparent with red border    |

Size modifiers:

| Modifier         | Min-height | Padding          |
| ---------------- | ---------- | ---------------- |
| `.pf-btn--sm`    | `34px`     | `0.35rem 0.75rem` |
| (default)        | `42px`     | `0.55rem 1.15rem` |
| `.pf-btn--lg`    | `48px`     | `0.75rem 1.6rem`  |
| `.pf-btn--icon`  | `40px`     | `0.5rem` (square) |

```html
<a class="pf-btn pf-btn--primary pf-btn--lg" href="/register">Get Started →</a>
<button class="pf-btn pf-btn--secondary">Cancel</button>
<button class="pf-btn pf-btn--ghost pf-btn--sm">Dismiss</button>
```

### Cards

Base class: `.pf-card`

| Modifier               | Effect                        |
| ---------------------- | ----------------------------- |
| `.pf-card--interactive` | Hover lift + border highlight |
| `.pf-card--flat`        | No shadow                     |
| `.pf-card--glass`       | Semi-transparent + backdrop blur |
| `.pf-card--accent`      | Left blue border              |
| `.pf-card--accent-purple` | Left purple border          |
| `.pf-card--tint`        | Subtle gradient background    |

Sub-elements: `.pf-card__header`, `.pf-card__body`, `.pf-card__footer`

```html
<div class="pf-card pf-card--interactive">
    <div class="pf-card__header">
        <h3>Card Title</h3>
    </div>
    <div class="pf-card__body">
        <p>Card content goes here.</p>
    </div>
</div>
```

### Badges

Base class: `.pf-badge`

| Modifier             | Color scheme     |
| -------------------- | ---------------- |
| `.pf-badge--blue`    | Blue (primary)   |
| `.pf-badge--green`   | Green (success)  |
| `.pf-badge--yellow`  | Yellow (warning) |
| `.pf-badge--red`     | Red (danger)     |
| `.pf-badge--purple`  | Purple           |
| `.pf-badge--gray`    | Neutral gray     |
| `.pf-badge--outline` | Border only      |

```html
<span class="pf-badge pf-badge--green">Active</span>
<span class="pf-badge pf-badge--yellow">Pending</span>
```

### Alerts

Base class: `.pf-alert` (or use `role="alert"` / `role="status"`)

| Modifier              | Color scheme              |
| --------------------- | ------------------------- |
| `.pf-alert--success`  | Green (auto for `role="status"`) |
| `.pf-alert--error`    | Red (auto for `role="alert"`)    |
| `.pf-alert--info`     | Blue                      |
| `.pf-alert--warning`  | Yellow                    |

```html
<div class="pf-alert pf-alert--info">
    <span>Informational message here.</span>
</div>
```

### Forms

| Element           | Class         | Notes                        |
| ----------------- | ------------- | ---------------------------- |
| Form wrapper      | `.pf-form`    | Max-width 760px              |
| Field group       | `.pf-form-group` or `.pf-field` | Bottom margin spacing |
| Label             | `.pf-label`   | Bold (600), font-size 0.9rem |
| Required indicator| `.pf-required-star` | Red asterisk `*` (`--pf-danger`) |
| Text input        | `.pf-input`   | Auto-styled via type selectors |
| Select            | `.pf-select`  | Custom SVG chevron arrow, cross-browser `-webkit-appearance: none` |
| Textarea          | `.pf-textarea` | Min-height 110px            |
| Hint text         | `.pf-hint`    | Small muted text             |
| Choice card       | `.pf-choice-card` | Radio/checkbox card option |

```html
<div class="pf-form-group">
    <label class="pf-label" for="name">
        <span>Project Name</span>
        <span class="pf-required-star">*</span>
    </label>
    <input id="name" type="text" class="pf-input" placeholder="Enter name..." required>
    <p class="pf-hint">This will be used as the display name.</p>
</div>

<!-- Form / Wizard Action Bar: Cancel/Back on LEFT, Submit/Continue on RIGHT -->
<div style="margin-top: 2rem; padding-top: 1.5rem; border-top: 1px solid var(--pf-border); display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 1rem;">
    <button type="submit" form="wizard-cancel-form" class="pf-btn pf-btn--ghost" style="color: var(--pf-text-muted);">
        Assistent abbrechen
    </button>
    <button type="submit" class="pf-btn pf-btn--primary">
        Weiter zum nächsten Schritt →
    </button>
</div>
```

### Page Headers

```html
<div class="pf-page-header">
    <div class="pf-page-header__left">
        <a class="pf-page-header__back" href="/projects">← Back</a>
        <h1>Page Title</h1>
        <p class="pf-page-header__subtitle">Description text</p>
    </div>
    <div class="pf-page-header__actions">
        <a class="pf-btn pf-btn--primary" href="/action">Action</a>
    </div>
</div>
```

### Stat Cards

```html
<div class="pf-stats-row">
    <div class="pf-stat-card">
        <div class="pf-stat-icon">
            <svg><!-- icon --></svg>
        </div>
        <div class="pf-stat-info">
            <span class="pf-stat-val">42</span>
            <span class="pf-stat-lbl">Active Tasks</span>
        </div>
    </div>
</div>
```

Icon color modifiers: `.pf-stat-icon--green`, `.pf-stat-icon--yellow`, `.pf-stat-icon--purple`

### Empty States

```html
<div class="pf-empty-state">
    <div class="pf-empty-state__icon">
        <svg><!-- icon --></svg>
    </div>
    <div class="pf-empty-state__title">No Projects Yet</div>
    <p class="pf-empty-state__text">Create your first project to get started.</p>
    <a class="pf-btn pf-btn--primary" href="/projects/new">Create Project</a>
</div>
```

---

## 5. Layout Utilities

| Class             | Behavior                                     |
| ----------------- | -------------------------------------------- |
| `.pf-stack`       | Vertical spacing between children (1.15rem)  |
| `.pf-cluster`     | Horizontal flex-wrap with gap (0.75rem)      |
| `.pf-flex-between` | Flex row, space-between alignment           |
| `.pf-grid`        | CSS Grid base with 1.25rem gap               |
| `.pf-grid--2`     | 2-column responsive grid (min 300px)         |
| `.pf-grid--3`     | 3-column responsive grid (min 260px)         |

---

## 6. Transitions & Animations

- **Global transition**: `var(--pf-transition)` = `all 0.2s cubic-bezier(0.16, 1, 0.3, 1)`
- **Hover lift**: `transform: translateY(-2px)` (cards, buttons)
- **Focus ring**: `box-shadow: 0 0 0 3.5px rgba(59, 130, 246, 0.15)` + blue border
- **Entry animations** (homepage): `pf-fade-in`, `pf-slide-up`

---

## 7. Layout Structure

Every page follows this structure:

```html
<!doctype html>
<html lang="de" xmlns:th="http://www.thymeleaf.org">
<head>
    <th:block th:replace="~{fragments/layout :: head('Page Title')}" />
</head>
<body>
    <th:block th:replace="~{fragments/layout :: site-header}" />

    <main>
        <!-- Page content -->
    </main>

    <th:block th:replace="~{fragments/layout :: site-footer}" />
</body>
</html>
```

- `<main>` automatically fills remaining viewport height (sticky footer via flexbox)
- Max content width: `var(--pf-max-width)` = `1200px`
- Content is horizontally centered with side padding

---

## 8. Auth-Aware Navigation

The site header uses Spring Security's Thymeleaf integration:

- **Anonymous users** see: Brand → Login + Register buttons
- **Authenticated users** see: Brand → Projekte / Vorlagen / Neu → Workspace + Abmelden

Use `sec:authorize="isAuthenticated()"` and `sec:authorize="!isAuthenticated()"` attributes for conditional rendering.

---

## 9. Quick Reference: Common Patterns

### Styled Button Link
```html
<a class="pf-btn pf-btn--primary" th:href="@{/path}" style="text-decoration: none;">
    Label
</a>
```

### Card with Data List
```html
<div class="pf-card">
    <dl>
        <dt>Key</dt><dd>Value</dd>
        <dt>Key</dt><dd>Value</dd>
    </dl>
</div>
```

### Responsive Grid
```html
<div class="pf-grid pf-grid--3">
    <div class="pf-card">...</div>
    <div class="pf-card">...</div>
    <div class="pf-card">...</div>
</div>
```

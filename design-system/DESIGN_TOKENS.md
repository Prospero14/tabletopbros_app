# Design Tokens - TableTop Bros

Документация дизайн-системы, извлечённая из Android проекта и адаптированная для использования в React/Web проекте.

## Источник

Все дизайн-токены извлечены из Android проекта:
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/drawable/*.xml`

## Цвета

### Primary Colors (Deep Purple)
- **Primary**: `#673AB7`
- **Primary Dark**: `#512DA8`
- **Primary Light**: `#9575CD`

### Secondary Colors (Juicy Orange)
- **Secondary**: `#FF6D00`
- **Secondary Dark**: `#E65100`
- **Secondary Light**: `#FF9E40`

### Accent Colors (Warm Orange)
- **Accent**: `#FF9A5C`
- **Accent Light**: `#FFB88C`

### Background Colors
- **Background**: `#FAFAFA`
- **Background Dark**: `#121212`
- **Surface**: `#FFFFFF`
- **Surface Dark**: `#1E1E1E`

### Text Colors
- **Text Primary**: `#212121`
- **Text Secondary**: `#757575`
- **Text On Primary**: `#FFFFFF`
- **Text On Secondary**: `#FFFFFF`

### Chat Colors
- **Chat Bubble Own**: `rgba(179, 157, 219, 0.5)` - полупрозрачный фиолетовый
- **Chat Bubble Other**: `rgba(255, 255, 255, 0.88)` - полупрозрачный белый
- **Poll Background**: `rgba(255, 158, 64, 0.5)` - полупрозрачный оранжевый

## Размеры

### Spacing (16dp = 1rem)
- **XS**: 4dp (0.25rem)
- **SM**: 8dp (0.5rem)
- **MD**: 16dp (1rem)
- **LG**: 24dp (1.5rem)
- **XL**: 32dp (2rem)

### Border Radius
- **SM**: 8dp (0.5rem)
- **MD**: 16dp (1rem) - стандартный для кнопок и карточек
- **LG**: 24dp (1.5rem)
- **XL**: 32dp (2rem)

## Использование в Android

### Colors
```xml
<color name="primary">#673AB7</color>
<color name="secondary">#FF6D00</color>
<color name="accent">#FF9A5C</color>
```

### Dimensions
```xml
<dimen name="activity_horizontal_margin">16dp</dimen>
<dimen name="chat_bubble_radius">16dp</dimen>
```

## Использование в React/Web

См. файлы:
- `src/styles/design-tokens.css` - CSS переменные
- `src/styles/mobile-globals.css` - адаптивные стили

## Соответствие Android → Web

| Android | Web/CSS |
|---------|---------|
| `@color/primary` | `var(--color-primary)` или `#673AB7` |
| `@color/secondary` | `var(--color-secondary)` или `#FF6D00` |
| `@color/accent` | `var(--color-accent)` или `#FF9A5C` |
| `16dp` | `1rem` или `var(--spacing-md)` |
| `12dp` corner radius | `var(--radius-md)` или `rounded-xl` |
| `@drawable/bg_cloud_dialog` | `.surface-glass` |
| `@drawable/rounded_background` | `.card` |


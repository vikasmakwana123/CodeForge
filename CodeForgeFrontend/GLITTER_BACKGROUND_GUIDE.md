# GlitterBackground Component - Configuration Guide

## Overview
The `GlitterBackground` component is a production-ready React component that creates an interactive glitter/particle background using pure HTML5 Canvas. Particles respond to cursor movement with realistic physics.

## Installation & Usage

### 1. Import the component in your App.jsx:
```jsx
import GlitterBackground from './components/GlitterBackground'

function App() {
  return (
    <>
      <GlitterBackground color="rgba(255, 255, 255, 0.8)" />
      {/* Rest of your app */}
    </>
  )
}
```

### 2. Props
- `color` (optional): The color of the glitter particles
  - Default: `"rgba(255, 255, 255, 0.8)"` (white with 80% opacity)
  - Examples: `"rgba(255, 255, 255, 0.8)"`, `"#ffffff"`, `"#f5c7ff"`, `"#ffd6a5"`
  - Note: For best results, use rgba() with opacity for the glow effect

## Configuration - Customizing the Effect

Open `GlitterBackground.jsx` and modify the `CONFIG` object to change the behavior:

```javascript
const CONFIG = {
  particleDensity: 0.00008,      // Particles per pixel
  interactionRadius: 120,         // How far the cursor affects particles
  repulsionStrength: 2,           // How strongly particles are pushed
  friction: 0.92,                 // Movement damping
  returnStrength: 0.015,          // How strongly particles return
  particleSizeMin: 0.5,           // Minimum particle size
  particleSizeMax: 2.5,           // Maximum particle size
  twinkleSpeed: 0.05,             // Speed of twinkling animation
  floatStrength: 0.3,             // Subtle organic movement
};
```

### Customization Examples

#### Make the effect STRONGER:
```javascript
particleDensity: 0.0001,        // More particles (0.00008 → 0.0001)
interactionRadius: 150,         // Larger interaction area (120 → 150)
repulsionStrength: 3,           // Stronger push (2 → 3)
```

#### Make the effect WEAKER:
```javascript
particleDensity: 0.00005,       // Fewer particles (0.00008 → 0.00005)
interactionRadius: 80,          // Smaller interaction area (120 → 80)
repulsionStrength: 1,           // Weaker push (2 → 1)
```

#### Make particles return faster:
```javascript
returnStrength: 0.03,           // Return faster (0.015 → 0.03)
friction: 0.88,                 // More drag (0.92 → 0.88)
```

#### Make particles drift more:
```javascript
floatStrength: 0.5,             // More floating (0.3 → 0.5)
twinkleSpeed: 0.1,              // Faster twinkling (0.05 → 0.1)
```

#### Change particle sizes:
```javascript
particleSizeMin: 1,             // Larger minimum (0.5 → 1)
particleSizeMax: 3.5,           // Larger maximum (2.5 → 3.5)
```

### Config Parameter Explanations

| Parameter | Range | Effect |
|-----------|-------|--------|
| **particleDensity** | 0.00001 - 0.0002 | Higher = more particles. 0.00008 is balanced. |
| **interactionRadius** | 50 - 200 | Larger = cursor affects particles from farther away. |
| **repulsionStrength** | 0.5 - 5 | Higher = particles are pushed away more forcefully. |
| **friction** | 0.85 - 0.98 | Lower = more drag/resistance. 0.92 is smooth. |
| **returnStrength** | 0.005 - 0.05 | Higher = particles snap back to origin faster. |
| **particleSizeMin** | 0.3 - 1.5 | Minimum size of particles. |
| **particleSizeMax** | 1.5 - 4 | Maximum size of particles. |
| **twinkleSpeed** | 0.01 - 0.2 | Higher = faster twinkling effect. |
| **floatStrength** | 0 - 1 | How much particles drift when cursor is away. 0 = static. |

## Features

✨ **Interactive Physics**: Particles are repelled by cursor with realistic velocity/inertia
✨ **Smooth Animation**: 60fps animation using requestAnimationFrame
✨ **Performance Optimized**: Uses single canvas, no React state in animation loop
✨ **Responsive**: Automatically handles window resizing and high-DPI displays
✨ **No Pointer Events**: Won't interfere with buttons, links, or other UI
✨ **Beautiful Sparkles**: Mix of dots and 4-point star sparkles with glow
✨ **Customizable**: All colors and behavior easily configurable

## Technical Details

- **Pure JavaScript**: No external dependencies (No Three.js, GSAP, etc.)
- **Canvas API**: Single HTML5 canvas for optimal performance
- **Ref-based**: Particles stored in React ref, not state
- **Event Cleanup**: Proper cleanup on unmount
- **DPI Aware**: Handles devicePixelRatio for crisp rendering

## Performance Notes

- Works smoothly with 2000-5000 particles on most devices
- If performance drops, reduce `particleDensity`
- The component uses `pointer-events: none` so it never blocks interactions
- All calculations are lightweight and optimized

## Color Customization Examples

```jsx
// Default white
<GlitterBackground />

// Purple/Lavender
<GlitterBackground color="rgba(245, 199, 255, 0.9)" />

// Peach/Coral
<GlitterBackground color="rgba(255, 214, 165, 0.8)" />

// Cyan/Blue
<GlitterBackground color="rgba(139, 233, 253, 0.7)" />

// Gold
<GlitterBackground color="rgba(255, 215, 0, 0.8)" />
```

## Troubleshooting

**Particles not moving with cursor?**
- Check that `interactionRadius` is not too small
- Verify `repulsionStrength` is not 0
- Ensure mouse events are being fired

**Too many/too few particles?**
- Adjust `particleDensity` up or down
- Higher values = more particles = more CPU usage

**Particles jerky or jittery?**
- Increase `friction` (0.92 → 0.95)
- Decrease `repulsionStrength`
- Lower particle count

**Cursor effect too subtle?**
- Increase `repulsionStrength`
- Increase `interactionRadius`
- Decrease `friction`

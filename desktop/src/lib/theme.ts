export type AppearanceMode = 'dark' | 'light';

/**
 * Exact mobile Veyl color tokens from VeylDesignSystem.kt:
 * Default Theme: Monochrome Carbon (Mastering studio minimalism with carbon & crisp platinum)
 * Dark: Carbon Obsidian (#121212) & Crisp Platinum (#FFFFFF)
 * Light: Pure minimal porcelain (#FAFAFA) & Jet Carbon (#18181B)
 */
export const DARK_THEME = {
  bgVoid: '#121212',
  surfacePanel: '#18181A',
  surfaceCard: '#1E1F24',
  surfaceCardHover: '#26272E',
  surfacePill: '#25262C',
  surfaceHighlight: '#31333C',
  accent: '#FFFFFF',
  accentBright: '#FFFFFF',
  accentDim: 'rgba(255, 255, 255, 0.20)',
  accentGlow: 'rgba(255, 255, 255, 0.12)',
  textPrimary: '#F8FAFC',
  textSecondary: '#94A3B8',
  textMuted: '#64748B',
  borderHairline: 'rgba(255, 255, 255, 0.08)',
  borderActive: 'rgba(255, 255, 255, 0.40)',
  favorite: '#FFFFFF',
};

export const LIGHT_THEME = {
  bgVoid: '#FAFAFA',
  surfacePanel: '#FFFFFF',
  surfaceCard: '#F4F4F5',
  surfaceCardHover: '#E4E4E7',
  surfacePill: '#E4E4E7',
  surfaceHighlight: '#D4D4D8',
  accent: '#18181B',
  accentBright: '#000000',
  accentDim: 'rgba(0, 0, 0, 0.20)',
  accentGlow: 'rgba(0, 0, 0, 0.08)',
  textPrimary: '#09090B',
  textSecondary: '#52525B',
  textMuted: '#71717A',
  borderHairline: 'rgba(0, 0, 0, 0.08)',
  borderActive: 'rgba(0, 0, 0, 0.40)',
  favorite: '#18181B',
};

export function applyAppearance(isDark: boolean) {
  const t = isDark ? DARK_THEME : LIGHT_THEME;
  const root = document.documentElement;
  root.style.setProperty('--bg-void', t.bgVoid);
  root.style.setProperty('--surface-panel', t.surfacePanel);
  root.style.setProperty('--surface-card', t.surfaceCard);
  root.style.setProperty('--surface-card-hover', t.surfaceCardHover);
  root.style.setProperty('--surface-pill', t.surfacePill);
  root.style.setProperty('--surface-highlight', t.surfaceHighlight);
  root.style.setProperty('--signal-accent', t.accent);
  root.style.setProperty('--signal-accent-bright', t.accentBright);
  root.style.setProperty('--signal-accent-dim', t.accentDim);
  root.style.setProperty('--signal-accent-glow', t.accentGlow);
  root.style.setProperty('--text-primary', t.textPrimary);
  root.style.setProperty('--text-secondary', t.textSecondary);
  root.style.setProperty('--text-muted', t.textMuted);
  root.style.setProperty('--border-hairline', t.borderHairline);
  root.style.setProperty('--border-active', t.borderActive);
  root.style.setProperty('--favorite-pink', t.favorite);
  if (isDark) {
    root.classList.add('dark');
    root.classList.remove('light');
  } else {
    root.classList.add('light');
    root.classList.remove('dark');
  }
}

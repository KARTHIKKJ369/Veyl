<script lang="ts">
  let { children } = $props();
</script>

{@render children()}

<style>
  :global(:root) {
    --signal-accent: #FFFFFF;
    --signal-accent-bright: #FFFFFF;
    --signal-accent-glow: rgba(255, 255, 255, 0.12);
    --signal-accent-dim: rgba(255, 255, 255, 0.20);

    --bg-void: #000000;
    --surface-panel: #121212;
    --surface-card: #181818;
    --surface-card-hover: #242424;
    --surface-pill: rgba(255, 255, 255, 0.08);
    --surface-highlight: #2a2a2a;

    --favorite-pink: #1DB954;
    --signal-amber: #E2E8F0;
    --cyber-cyan: #B3B3B3;
    --spectral-green: #1DB954;
    --danger-red: #EF4444;

    --text-primary: #FFFFFF;
    --text-secondary: #B3B3B3;
    --text-muted: #727272;

    --border-hairline: rgba(255, 255, 255, 0.07);
    --border-active: rgba(255, 255, 255, 0.25);

    --radius-sm: 6px;
    --radius-md: 8px;
    --radius-lg: 12px;
    --radius-pill: 9999px;

    --font-sans: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    --font-mono: 'JetBrains Mono', 'SF Mono', Consolas, monospace;
  }

  :global(:root.light) {
    --signal-accent: #18181B;
    --signal-accent-bright: #000000;
    --signal-accent-glow: rgba(0, 0, 0, 0.08);
    --signal-accent-dim: rgba(0, 0, 0, 0.20);

    --bg-void: #FAFAFA;
    --surface-panel: #FFFFFF;
    --surface-card: #F4F4F5;
    --surface-card-hover: #E4E4E7;
    --surface-pill: #E4E4E7;
    --surface-highlight: #D4D4D8;

    --favorite-pink: #18181B;
    --signal-amber: #334155;
    --cyber-cyan: #52525B;
    --spectral-green: #52525B;
    --danger-red: #DC2626;

    --text-primary: #09090B;
    --text-secondary: #52525B;
    --text-muted: #71717A;

    --border-hairline: rgba(0, 0, 0, 0.08);
    --border-active: rgba(0, 0, 0, 0.40);
  }


  :global(*, *::before, *::after) {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
  }

  :global(body) {
    background-color: var(--bg-void);
    color: var(--text-primary);
    font-family: var(--font-sans);
    font-size: 13px;
    line-height: 1.5;
    overflow: hidden;
    user-select: none;
    -webkit-font-smoothing: antialiased;
    height: 100vh;
    width: 100vw;
  }

  :global(button) {
    font-family: inherit;
    border: none;
    background: none;
    cursor: pointer;
    color: inherit;
  }

  :global(input) {
    font-family: inherit;
    color: inherit;
  }

  :global(::-webkit-scrollbar) {
    width: 6px;
    height: 6px;
  }

  :global(::-webkit-scrollbar-track) {
    background: transparent;
  }

  :global(::-webkit-scrollbar-thumb) {
    background: rgba(255, 255, 255, 0.15);
    border-radius: 4px;
  }

  :global(::-webkit-scrollbar-thumb:hover) {
    background: rgba(255, 255, 255, 0.3);
  }
</style>

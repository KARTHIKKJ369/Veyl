<script lang="ts">
  let { children } = $props();
</script>

{@render children()}

<style>
  :global(:root) {
    --bg-void: #08090a;
    --surface-panel: #111215;
    --surface-card: #18191e;
    --surface-card-hover: #212228;
    --surface-pill: rgba(255, 255, 255, 0.06);

    --signal-green: #ccff00;
    --signal-green-dim: rgba(204, 255, 0, 0.15);
    --signal-green-glow: rgba(204, 255, 0, 0.35);

    --text-primary: #ffffff;
    --text-secondary: rgba(255, 255, 255, 0.68);
    --text-muted: rgba(255, 255, 255, 0.38);

    --border-hairline: rgba(255, 255, 255, 0.08);
    --border-active: rgba(204, 255, 0, 0.5);

    --radius-sm: 6px;
    --radius-md: 10px;
    --radius-lg: 16px;
    --radius-pill: 9999px;

    --font-sans: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    --font-mono: 'JetBrains Mono', 'SF Mono', Consolas, monospace;
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

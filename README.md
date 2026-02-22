<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>@atomiqlab/react-native-mapbox-navigation</title>
<link href="https://fonts.googleapis.com/css2?family=Syne:wght@400;600;700;800&family=JetBrains+Mono:wght@400;500;600&family=DM+Sans:ital,wght@0,300;0,400;0,500;1,300&display=swap" rel="stylesheet" />
<style>
  :root {
    --bg: #080c14;
    --surface: #0d1422;
    --surface2: #111827;
    --border: rgba(99,179,237,0.12);
    --border-strong: rgba(99,179,237,0.25);
    --blue: #3b82f6;
    --blue-light: #60a5fa;
    --blue-glow: #1d4ed8;
    --cyan: #22d3ee;
    --green: #4ade80;
    --yellow: #fbbf24;
    --red: #f87171;
    --text: #e2e8f0;
    --text-muted: #64748b;
    --text-dim: #94a3b8;
    --accent: #0ea5e9;
  }

  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

  html { scroll-behavior: smooth; }

  body {
    background: var(--bg);
    color: var(--text);
    font-family: 'DM Sans', sans-serif;
    font-size: 15px;
    line-height: 1.7;
    min-height: 100vh;
    overflow-x: hidden;
  }

  /* Background grid */
  body::before {
    content: '';
    position: fixed;
    inset: 0;
    background-image:
      linear-gradient(rgba(59,130,246,0.03) 1px, transparent 1px),
      linear-gradient(90deg, rgba(59,130,246,0.03) 1px, transparent 1px);
    background-size: 48px 48px;
    pointer-events: none;
    z-index: 0;
  }

  .container {
    max-width: 900px;
    margin: 0 auto;
    padding: 0 32px 100px;
    position: relative;
    z-index: 1;
  }

  /* ── HERO ── */
  .hero {
    padding: 80px 0 60px;
    border-bottom: 1px solid var(--border);
    margin-bottom: 64px;
    position: relative;
  }

  .hero::after {
    content: '';
    position: absolute;
    bottom: -1px;
    left: 0;
    width: 180px;
    height: 1px;
    background: linear-gradient(90deg, var(--blue), transparent);
  }

  .pkg-name {
    font-family: 'JetBrains Mono', monospace;
    font-size: 13px;
    color: var(--cyan);
    letter-spacing: 0.08em;
    margin-bottom: 20px;
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }

  .pkg-name::before {
    content: '';
    width: 28px;
    height: 1px;
    background: var(--cyan);
  }

  .hero h1 {
    font-family: 'Syne', sans-serif;
    font-size: clamp(36px, 6vw, 56px);
    font-weight: 800;
    line-height: 1.1;
    letter-spacing: -0.02em;
    margin-bottom: 20px;
    background: linear-gradient(135deg, #e2e8f0 0%, #60a5fa 50%, #22d3ee 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }

  .hero-sub {
    font-size: 17px;
    color: var(--text-dim);
    font-weight: 300;
    max-width: 560px;
    margin-bottom: 36px;
  }

  .badge-row {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .badge {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 5px 12px;
    border-radius: 999px;
    font-family: 'JetBrains Mono', monospace;
    font-size: 11px;
    font-weight: 500;
    letter-spacing: 0.04em;
    border: 1px solid;
  }

  .badge-blue   { background: rgba(59,130,246,0.1);  border-color: rgba(59,130,246,0.3);  color: #93c5fd; }
  .badge-cyan   { background: rgba(34,211,238,0.08); border-color: rgba(34,211,238,0.25); color: #67e8f9; }
  .badge-green  { background: rgba(74,222,128,0.08); border-color: rgba(74,222,128,0.25); color: #86efac; }
  .badge-yellow { background: rgba(251,191,36,0.08); border-color: rgba(251,191,36,0.25); color: #fde68a; }

  /* ── SECTION HEADERS ── */
  .section {
    margin-bottom: 56px;
  }

  .section-label {
    font-family: 'JetBrains Mono', monospace;
    font-size: 10px;
    letter-spacing: 0.15em;
    text-transform: uppercase;
    color: var(--blue-light);
    margin-bottom: 12px;
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .section-label::after {
    content: '';
    flex: 1;
    height: 1px;
    background: var(--border);
  }

  h2 {
    font-family: 'Syne', sans-serif;
    font-size: 26px;
    font-weight: 700;
    letter-spacing: -0.01em;
    color: #f1f5f9;
    margin-bottom: 20px;
  }

  h3 {
    font-family: 'Syne', sans-serif;
    font-size: 17px;
    font-weight: 600;
    color: #cbd5e1;
    margin: 28px 0 12px;
  }

  p {
    color: var(--text-dim);
    margin-bottom: 16px;
  }

  a { color: var(--blue-light); text-decoration: none; }
  a:hover { text-decoration: underline; }

  /* ── FEATURES GRID ── */
  .features-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
    gap: 14px;
    margin-bottom: 8px;
  }

  .feature-card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 20px;
    transition: border-color 0.2s, transform 0.2s;
    position: relative;
    overflow: hidden;
  }

  .feature-card::before {
    content: '';
    position: absolute;
    top: 0; left: 0;
    width: 100%; height: 2px;
    background: linear-gradient(90deg, var(--blue), transparent);
    opacity: 0;
    transition: opacity 0.2s;
  }

  .feature-card:hover {
    border-color: var(--border-strong);
    transform: translateY(-2px);
  }

  .feature-card:hover::before { opacity: 1; }

  .feature-icon {
    font-size: 22px;
    margin-bottom: 10px;
    display: block;
  }

  .feature-title {
    font-family: 'Syne', sans-serif;
    font-size: 14px;
    font-weight: 600;
    color: #e2e8f0;
    margin-bottom: 6px;
  }

  .feature-desc {
    font-size: 13px;
    color: var(--text-muted);
    line-height: 1.5;
    margin: 0;
  }

  /* ── REQUIREMENTS ── */
  .req-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
  }

  .req-card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 16px 20px;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .req-label {
    font-family: 'JetBrains Mono', monospace;
    font-size: 10px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: var(--text-muted);
  }

  .req-value {
    font-family: 'Syne', sans-serif;
    font-size: 16px;
    font-weight: 700;
    color: var(--cyan);
  }

  /* ── CODE BLOCKS ── */
  .code-block {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 12px;
    overflow: hidden;
    margin: 16px 0;
  }

  .code-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 16px;
    border-bottom: 1px solid var(--border);
    background: rgba(255,255,255,0.02);
  }

  .code-lang {
    font-family: 'JetBrains Mono', monospace;
    font-size: 11px;
    color: var(--text-muted);
    letter-spacing: 0.05em;
  }

  .code-dots {
    display: flex;
    gap: 6px;
  }

  .code-dots span {
    width: 10px;
    height: 10px;
    border-radius: 50%;
  }

  .dot-red    { background: #f87171; }
  .dot-yellow { background: #fbbf24; }
  .dot-green  { background: #4ade80; }

  pre {
    padding: 20px;
    overflow-x: auto;
    font-family: 'JetBrains Mono', monospace;
    font-size: 13px;
    line-height: 1.7;
    color: #94a3b8;
  }

  code {
    font-family: 'JetBrains Mono', monospace;
    font-size: 12.5px;
  }

  /* Syntax colors */
  .kw  { color: #c084fc; }
  .str { color: #86efac; }
  .fn  { color: #60a5fa; }
  .cm  { color: #475569; font-style: italic; }
  .num { color: #fb923c; }
  .prop{ color: #22d3ee; }
  .tag { color: #f87171; }

  /* Inline code */
  p code, li code, td code {
    background: rgba(99,179,237,0.1);
    border: 1px solid rgba(99,179,237,0.15);
    border-radius: 4px;
    padding: 1px 6px;
    font-size: 12px;
    color: #93c5fd;
  }

  /* ── STEPS ── */
  .steps {
    counter-reset: step;
    display: flex;
    flex-direction: column;
    gap: 0;
  }

  .step {
    display: flex;
    gap: 20px;
    position: relative;
    padding-bottom: 32px;
  }

  .step:last-child { padding-bottom: 0; }

  .step-num {
    counter-increment: step;
    flex-shrink: 0;
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: linear-gradient(135deg, var(--blue-glow), var(--blue));
    display: flex;
    align-items: center;
    justify-content: center;
    font-family: 'Syne', sans-serif;
    font-size: 13px;
    font-weight: 700;
    color: white;
    position: relative;
    z-index: 1;
  }

  .step:not(:last-child) .step-num::after {
    content: '';
    position: absolute;
    top: 100%;
    left: 50%;
    transform: translateX(-50%);
    width: 1px;
    height: calc(100% + 16px);
    background: var(--border);
    margin-top: 8px;
  }

  .step-body {
    flex: 1;
    padding-top: 4px;
  }

  .step-title {
    font-family: 'Syne', sans-serif;
    font-size: 15px;
    font-weight: 600;
    color: #e2e8f0;
    margin-bottom: 8px;
  }

  /* ── API TABLE ── */
  .api-group {
    margin-bottom: 32px;
  }

  .api-list {
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .api-item {
    display: flex;
    align-items: baseline;
    gap: 12px;
    padding: 10px 14px;
    border-radius: 8px;
    transition: background 0.15s;
  }

  .api-item:hover { background: var(--surface); }

  .api-fn {
    font-family: 'JetBrains Mono', monospace;
    font-size: 13px;
    color: var(--blue-light);
    white-space: nowrap;
    min-width: 280px;
  }

  .api-desc {
    font-size: 13px;
    color: var(--text-muted);
    line-height: 1.4;
  }

  .api-tag {
    font-family: 'JetBrains Mono', monospace;
    font-size: 10px;
    padding: 2px 7px;
    border-radius: 4px;
    white-space: nowrap;
    margin-left: auto;
  }

  .tag-ios     { background: rgba(59,130,246,0.12); color: #93c5fd; border: 1px solid rgba(59,130,246,0.2); }
  .tag-android { background: rgba(74,222,128,0.1);  color: #86efac; border: 1px solid rgba(74,222,128,0.2); }
  .tag-both    { background: rgba(251,191,36,0.08); color: #fde68a; border: 1px solid rgba(251,191,36,0.2); }

  /* ── FEATURE MATRIX TABLE ── */
  .matrix-wrap {
    overflow-x: auto;
    border-radius: 12px;
    border: 1px solid var(--border);
  }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 13px;
  }

  thead th {
    background: var(--surface);
    padding: 12px 16px;
    text-align: left;
    font-family: 'Syne', sans-serif;
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: var(--text-muted);
    border-bottom: 1px solid var(--border);
    white-space: nowrap;
  }

  tbody tr {
    border-bottom: 1px solid rgba(99,179,237,0.05);
    transition: background 0.15s;
  }

  tbody tr:last-child { border-bottom: none; }
  tbody tr:hover { background: rgba(59,130,246,0.03); }

  tbody td {
    padding: 10px 16px;
    color: var(--text-dim);
    vertical-align: middle;
  }

  tbody td:first-child {
    font-family: 'JetBrains Mono', monospace;
    font-size: 12px;
    color: #94a3b8;
  }

  .status {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-family: 'JetBrains Mono', monospace;
    font-size: 11px;
    padding: 2px 8px;
    border-radius: 4px;
    white-space: nowrap;
  }

  .status-yes     { background: rgba(74,222,128,0.08);  color: #86efac; border: 1px solid rgba(74,222,128,0.2); }
  .status-partial { background: rgba(251,191,36,0.08);  color: #fde68a; border: 1px solid rgba(251,191,36,0.2); }
  .status-noop    { background: rgba(248,113,113,0.08); color: #fca5a5; border: 1px solid rgba(248,113,113,0.2); }
  .status-na      { color: var(--text-muted); }

  /* ── ERROR CODES ── */
  .error-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 10px;
  }

  .error-card {
    background: rgba(248,113,113,0.05);
    border: 1px solid rgba(248,113,113,0.15);
    border-radius: 8px;
    padding: 12px 14px;
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .error-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #f87171;
    flex-shrink: 0;
  }

  .error-code {
    font-family: 'JetBrains Mono', monospace;
    font-size: 12px;
    color: #fca5a5;
  }

  /* ── SUPPORT BANNER ── */
  .support-banner {
    background: linear-gradient(135deg, rgba(59,130,246,0.08), rgba(34,211,238,0.05));
    border: 1px solid rgba(59,130,246,0.2);
    border-radius: 16px;
    padding: 32px;
    text-align: center;
    position: relative;
    overflow: hidden;
  }

  .support-banner::before {
    content: '';
    position: absolute;
    inset: 0;
    background: radial-gradient(ellipse at 50% 0%, rgba(59,130,246,0.08), transparent 70%);
    pointer-events: none;
  }

  .support-banner h2 {
    font-size: 22px;
    margin-bottom: 8px;
  }

  .support-banner p {
    max-width: 420px;
    margin: 0 auto 20px;
    font-size: 14px;
  }

  .kofi-btn {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    background: #ff5e5b;
    color: white;
    padding: 11px 24px;
    border-radius: 999px;
    font-family: 'Syne', sans-serif;
    font-size: 14px;
    font-weight: 700;
    text-decoration: none;
    transition: opacity 0.2s, transform 0.2s;
  }

  .kofi-btn:hover { opacity: 0.9; transform: scale(1.03); text-decoration: none; }

  /* ── PLATFORM NOTE ── */
  .platform-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 14px;
  }

  .platform-card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 20px;
  }

  .platform-name {
    font-family: 'Syne', sans-serif;
    font-size: 15px;
    font-weight: 700;
    margin-bottom: 10px;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .platform-name.ios  { color: #93c5fd; }
  .platform-name.and  { color: #86efac; }

  .platform-card ul {
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .platform-card li {
    font-size: 13px;
    color: var(--text-muted);
    display: flex;
    align-items: flex-start;
    gap: 8px;
  }

  .platform-card li::before {
    content: '→';
    color: var(--text-muted);
    font-size: 11px;
    margin-top: 2px;
    flex-shrink: 0;
  }

  /* ── TOKEN WARN ── */
  .warn-box {
    background: rgba(251,191,36,0.05);
    border: 1px solid rgba(251,191,36,0.2);
    border-left: 3px solid #fbbf24;
    border-radius: 0 8px 8px 0;
    padding: 14px 18px;
    font-size: 13.5px;
    color: #fde68a;
    margin: 16px 0;
    display: flex;
    gap: 10px;
    align-items: flex-start;
  }

  .warn-icon { flex-shrink: 0; margin-top: 1px; }

  /* divider */
  hr {
    border: none;
    border-top: 1px solid var(--border);
    margin: 48px 0;
  }

  /* ── RESPONSIVE ── */
  @media (max-width: 600px) {
    .req-grid { grid-template-columns: 1fr 1fr; }
    .platform-row { grid-template-columns: 1fr; }
    .api-fn { min-width: 180px; }
  }
</style>
</head>
<body>
<div class="container">

  <!-- HERO -->
  <div class="hero">
    <div class="pkg-name">@atomiqlab/react-native-mapbox-navigation</div>
    <h1>Native Mapbox<br>Navigation for Expo</h1>
    <p class="hero-sub">Full-featured, production-ready turn-by-turn navigation for iOS and Android — built on native Mapbox SDKs, wired for Expo.</p>
    <div class="badge-row">
      <span class="badge badge-blue">Expo SDK ≥ 50</span>
      <span class="badge badge-cyan">iOS 14+</span>
      <span class="badge badge-green">Android</span>
      <span class="badge badge-yellow">TypeScript</span>
    </div>
  </div>

  <!-- FEATURES -->
  <div class="section">
    <div class="section-label">Features</div>
    <h2>Everything you need to navigate</h2>
    <div class="features-grid">
      <div class="feature-card">
        <span class="feature-icon">🗺️</span>
        <div class="feature-title">Full-Screen Navigation</div>
        <p class="feature-desc">Launch native full-screen navigation instantly via <code>startNavigation</code>.</p>
      </div>
      <div class="feature-card">
        <span class="feature-icon">📦</span>
        <div class="feature-title">Embedded View</div>
        <p class="feature-desc">Drop <code>&lt;MapboxNavigationView&gt;</code> anywhere in your UI with full control.</p>
      </div>
      <div class="feature-card">
        <span class="feature-icon">📡</span>
        <div class="feature-title">Real-Time Events</div>
        <p class="feature-desc">Location, route progress, banner instructions, arrival, cancel, and error callbacks.</p>
      </div>
      <div class="feature-card">
        <span class="feature-icon">🎛️</span>
        <div class="feature-title">Runtime Controls</div>
        <p class="feature-desc">Mute, voice volume, distance unit, and language — all adjustable mid-navigation.</p>
      </div>
      <div class="feature-card">
        <span class="feature-icon">🎨</span>
        <div class="feature-title">Deep Customization</div>
        <p class="feature-desc">Camera mode, theme, map style, bottom sheet colors, typography, and visibility toggles.</p>
      </div>
      <div class="feature-card">
        <span class="feature-icon">⚡</span>
        <div class="feature-title">Expo Config Plugin</div>
        <p class="feature-desc">Automated native iOS and Android setup — zero manual configuration needed.</p>
      </div>
    </div>
  </div>

  <!-- REQUIREMENTS -->
  <div class="section">
    <div class="section-label">Requirements</div>
    <h2>Before you begin</h2>
    <div class="req-grid">
      <div class="req-card">
        <span class="req-label">Expo SDK</span>
        <span class="req-value">≥ 50</span>
      </div>
      <div class="req-card">
        <span class="req-label">iOS</span>
        <span class="req-value">14+</span>
      </div>
      <div class="req-card">
        <span class="req-label">Mapbox Token</span>
        <span class="req-value">pk… + sk…</span>
      </div>
    </div>
    <p style="margin-top:16px;">You'll need a Mapbox <strong>public token</strong> (<code>pk...</code>) and a <strong>downloads token</strong> (<code>sk...</code>) with <code>DOWNLOADS:READ</code> scope.</p>
  </div>

  <!-- INSTALLATION -->
  <div class="section">
    <div class="section-label">Installation</div>
    <h2>Get up and running</h2>

    <div class="steps">
      <div class="step">
        <div class="step-num">1</div>
        <div class="step-body">
          <div class="step-title">Install the package</div>
          <div class="code-block">
            <div class="code-header">
              <span class="code-lang">bash</span>
              <div class="code-dots"><span class="dot-red"></span><span class="dot-yellow"></span><span class="dot-green"></span></div>
            </div>
            <pre><span class="fn">npm</span> install @atomiqlab/react-native-mapbox-navigation</pre>
          </div>
        </div>
      </div>

      <div class="step">
        <div class="step-num">2</div>
        <div class="step-body">
          <div class="step-title">Add the config plugin</div>
          <div class="code-block">
            <div class="code-header">
              <span class="code-lang">app.json</span>
              <div class="code-dots"><span class="dot-red"></span><span class="dot-yellow"></span><span class="dot-green"></span></div>
            </div>
            <pre>{
  <span class="prop">"expo"</span>: {
    <span class="prop">"plugins"</span>: [<span class="str">"@atomiqlab/react-native-mapbox-navigation"</span>]
  }
}</pre>
          </div>
        </div>
      </div>

      <div class="step">
        <div class="step-num">3</div>
        <div class="step-body">
          <div class="step-title">Set environment variables</div>
          <div class="code-block">
            <div class="code-header">
              <span class="code-lang">.env</span>
              <div class="code-dots"><span class="dot-red"></span><span class="dot-yellow"></span><span class="dot-green"></span></div>
            </div>
            <pre><span class="prop">EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN</span>=pk.your_token_here
<span class="prop">MAPBOX_DOWNLOADS_TOKEN</span>=sk.your_downloads_token_here</pre>
          </div>
          <div class="warn-box">
            <span class="warn-icon">⚠️</span>
            <span>The config plugin validates tokens at prebuild time — missing or malformed tokens cause an immediate, descriptive failure so you never encounter silent runtime issues.</span>
          </div>
        </div>
      </div>

      <div class="step">
        <div class="step-num">4</div>
        <div class="step-body">
          <div class="step-title">Regenerate native projects</div>
          <div class="code-block">
            <div class="code-header">
              <span class="code-lang">bash</span>
              <div class="code-dots"><span class="dot-red"></span><span class="dot-yellow"></span><span class="dot-green"></span></div>
            </div>
            <pre><span class="fn">npx</span> expo prebuild --clean</pre>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- QUICK START -->
  <div class="section">
    <div class="section-label">Quick Start</div>
    <h2>Launch navigation in seconds</h2>

    <h3>Full-Screen Navigation</h3>
    <div class="code-block">
      <div class="code-header">
        <span class="code-lang">TypeScript</span>
        <div class="code-dots"><span class="dot-red"></span><span class="dot-yellow"></span><span class="dot-green"></span></div>
      </div>
      <pre><span class="kw">import</span> {
  startNavigation, stopNavigation,
  addLocationChangeListener,
  addRouteProgressChangeListener,
  addBannerInstructionListener,
  addArriveListener, addCancelNavigationListener, addErrorListener,
} <span class="kw">from</span> <span class="str">"@atomiqlab/react-native-mapbox-navigation"</span>;

<span class="kw">await</span> <span class="fn">startNavigation</span>({
  destination:  { latitude: <span class="num">37.7847</span>, longitude: <span class="num">-122.4073</span>, name: <span class="str">"Downtown"</span> },
  startOrigin:  { latitude: <span class="num">37.7749</span>, longitude: <span class="num">-122.4194</span> },
  shouldSimulateRoute: <span class="kw">true</span>,
  routeAlternatives: <span class="kw">true</span>,
  cameraMode: <span class="str">"following"</span>,
  uiTheme: <span class="str">"system"</span>,
  distanceUnit: <span class="str">"metric"</span>,
  language: <span class="str">"en"</span>,
});

<span class="cm">// Attach event listeners</span>
<span class="kw">const</span> subs = [
  <span class="fn">addLocationChangeListener</span>((loc) => console.<span class="fn">log</span>(loc)),
  <span class="fn">addRouteProgressChangeListener</span>((p) => console.<span class="fn">log</span>(p)),
  <span class="fn">addBannerInstructionListener</span>((i) => console.<span class="fn">log</span>(i.primaryText)),
  <span class="fn">addArriveListener</span>((e) => console.<span class="fn">log</span>(e)),
  <span class="fn">addCancelNavigationListener</span>(() => console.<span class="fn">log</span>(<span class="str">"cancelled"</span>)),
  <span class="fn">addErrorListener</span>((err) => console.<span class="fn">warn</span>(err)),
];

<span class="cm">// Cleanup</span>
subs.<span class="fn">forEach</span>((s) => s.<span class="fn">remove</span>());
<span class="kw">await</span> <span class="fn">stopNavigation</span>();</pre>
    </div>

    <h3>Embedded Navigation View</h3>
    <div class="code-block">
      <div class="code-header">
        <span class="code-lang">TSX</span>
        <div class="code-dots"><span class="dot-red"></span><span class="dot-yellow"></span><span class="dot-green"></span></div>
      </div>
      <pre><span class="kw">import</span> { MapboxNavigationView } <span class="kw">from</span> <span class="str">"@atomiqlab/react-native-mapbox-navigation"</span>;

<span class="tag">&lt;MapboxNavigationView</span>
  style={{ flex: <span class="num">1</span> }}
  destination={{ latitude: <span class="num">37.7847</span>, longitude: <span class="num">-122.4073</span>, name: <span class="str">"Downtown"</span> }}
  startOrigin={{ latitude: <span class="num">37.7749</span>, longitude: <span class="num">-122.4194</span> }}
  shouldSimulateRoute
  cameraMode=<span class="str">"following"</span>
  bottomSheet={{ enabled: <span class="kw">true</span>, showsTripProgress: <span class="kw">true</span> }}
  onBannerInstruction={(i) => console.<span class="fn">log</span>(i.primaryText)}
  onRouteProgressChange={(p) => console.<span class="fn">log</span>(p.fractionTraveled)}
  onError={(e) => console.<span class="fn">warn</span>(e.message)}
<span class="tag">/&gt;</span></pre>
    </div>

    <h3>Custom Overlay Bottom Sheet</h3>
    <div class="code-block">
      <div class="code-header">
        <span class="code-lang">TSX</span>
        <div class="code-dots"><span class="dot-red"></span><span class="dot-yellow"></span><span class="dot-green"></span></div>
      </div>
      <pre><span class="tag">&lt;MapboxNavigationView</span>
  style={{ flex: <span class="num">1</span> }}
  destination={{ latitude: <span class="num">37.7847</span>, longitude: <span class="num">-122.4073</span>, name: <span class="str">"Downtown"</span> }}
  startOrigin={{ latitude: <span class="num">37.7749</span>, longitude: <span class="num">-122.4194</span> }}
  bottomSheet={{
    enabled: <span class="kw">true</span>,
    mode: <span class="str">"overlay"</span>,
    initialState: <span class="str">"collapsed"</span>,
    collapsedHeight: <span class="num">120</span>,
    expandedHeight: <span class="num">340</span>,
    containerStyle: { backgroundColor: <span class="str">"#0f172a"</span> },
    handleStyle: { backgroundColor: <span class="str">"#93c5fd"</span> },
  }}
  renderBottomSheet={({ expanded, toggle }) => (
    <span class="tag">&lt;Pressable</span> onPress={toggle}<span class="tag">&gt;</span>
      <span class="tag">&lt;Text</span> style={{ color: <span class="str">"white"</span>, fontWeight: <span class="str">"700"</span> }}<span class="tag">&gt;</span>
        {expanded ? <span class="str">"Collapse"</span> : <span class="str">"Expand"</span>} sheet
      <span class="tag">&lt;/Text&gt;</span>
    <span class="tag">&lt;/Pressable&gt;</span>
  )}
<span class="tag">/&gt;</span></pre>
    </div>
  </div>

  <!-- API -->
  <div class="section">
    <div class="section-label">API Reference</div>
    <h2>Functions &amp; Events</h2>

    <div class="api-group">
      <h3>Core Functions</h3>
      <ul class="api-list">
        <li class="api-item">
          <span class="api-fn">startNavigation(options)</span>
          <span class="api-desc">Launch full-screen native navigation with full option set.</span>
          <span class="api-tag tag-both">iOS · Android</span>
        </li>
        <li class="api-item">
          <span class="api-fn">stopNavigation()</span>
          <span class="api-desc">Programmatically stop an active navigation session.</span>
          <span class="api-tag tag-both">iOS · Android</span>
        </li>
        <li class="api-item">
          <span class="api-fn">isNavigating()</span>
          <span class="api-desc">Returns a promise resolving to the current navigation state.</span>
          <span class="api-tag tag-both">iOS · Android</span>
        </li>
        <li class="api-item">
          <span class="api-fn">getNavigationSettings()</span>
          <span class="api-desc">Retrieve current navigation configuration snapshot.</span>
          <span class="api-tag tag-both">iOS · Android</span>
        </li>
        <li class="api-item">
          <span class="api-fn">setMuted(muted)</span>
          <span class="api-desc">Toggle voice guidance on or off at runtime.</span>
          <span class="api-tag tag-both">iOS · Android</span>
        </li>
        <li class="api-item">
          <span class="api-fn">setVoiceVolume(volume)</span>
          <span class="api-desc">Set voice volume between <code>0</code> and <code>1</code>.</span>
          <span class="api-tag tag-both">iOS · Android</span>
        </li>
        <li class="api-item">
          <span class="api-fn">setDistanceUnit(unit)</span>
          <span class="api-desc">Switch between <code>"metric"</code> and <code>"imperial"</code> at runtime.</span>
          <span class="api-tag tag-both">iOS · Android</span>
        </li>
        <li class="api-item">
          <span class="api-fn">setLanguage(language)</span>
          <span class="api-desc">Change voice and UI language using a BCP-47 locale string.</span>
          <span class="api-tag tag-both">iOS · Android</span>
        </li>
      </ul>
    </div>

    <div class="api-group">
      <h3>Event Listeners</h3>
      <ul class="api-list">
        <li class="api-item">
          <span class="api-fn">addLocationChangeListener(fn)</span>
          <span class="api-desc">Fires on every GPS position update during navigation.</span>
          <span class="api-tag tag-both">iOS · Android</span>
        </li>
        <li class="api-item">
          <span class="api-fn">addRouteProgressChangeListener(fn)</span>
          <span class="api-desc">Fires with route progress including <code>fractionTraveled</code>.</span>
          <span class="api-tag tag-both">iOS · Android</span>
        </li>
        <li class="api-item">
          <span class="api-fn">addBannerInstructionListener(fn)</span>
          <span class="api-desc">Fires on each turn instruction change with <code>primaryText</code>.</span>
          <span class="api-tag tag-both">iOS · Android</span>
        </li>
        <li class="api-item">
          <span class="api-fn">addArriveListener(fn)</span>
          <span class="api-desc">Fires when the user reaches the destination.</span>
          <span class="api-tag tag-both">iOS · Android</span>
        </li>
        <li class="api-item">
          <span class="api-fn">addCancelNavigationListener(fn)</span>
          <span class="api-desc">Fires when the user cancels navigation.</span>
          <span class="api-tag tag-both">iOS · Android</span>
        </li>
        <li class="api-item">
          <span class="api-fn">addErrorListener(fn)</span>
          <span class="api-desc">Fires on navigation errors with <code>code</code> and <code>message</code>.</span>
          <span class="api-tag tag-both">iOS · Android</span>
        </li>
        <li class="api-item">
          <span class="api-fn">addBottomSheetActionPressListener(fn)</span>
          <span class="api-desc">Fires when a custom bottom sheet action is tapped.</span>
          <span class="api-tag tag-ios">iOS</span>
        </li>
        <li class="api-item">
          <span class="api-fn">addDestinationPreviewListener(fn)</span>
          <span class="api-desc">Fires when destination preview is shown.</span>
          <span class="api-tag tag-android">Android</span>
        </li>
        <li class="api-item">
          <span class="api-fn">addDestinationChangedListener(fn)</span>
          <span class="api-desc">Fires when the active destination changes.</span>
          <span class="api-tag tag-android">Android</span>
        </li>
      </ul>
    </div>
  </div>

  <!-- ERROR CODES -->
  <div class="section">
    <div class="section-label">Diagnostics</div>
    <h2>Error Codes</h2>
    <p>Subscribe via <code>addErrorListener</code> or the <code>onError</code> prop to surface these during development and production.</p>
    <div class="error-grid">
      <div class="error-card"><div class="error-dot"></div><span class="error-code">MAPBOX_TOKEN_INVALID</span></div>
      <div class="error-card"><div class="error-dot"></div><span class="error-code">MAPBOX_TOKEN_FORBIDDEN</span></div>
      <div class="error-card"><div class="error-dot"></div><span class="error-code">MAPBOX_RATE_LIMITED</span></div>
      <div class="error-card"><div class="error-dot"></div><span class="error-code">ROUTE_FETCH_FAILED</span></div>
      <div class="error-card"><div class="error-dot"></div><span class="error-code">CURRENT_LOCATION_UNAVAILABLE</span></div>
      <div class="error-card"><div class="error-dot"></div><span class="error-code">INVALID_COORDINATES</span></div>
    </div>
  </div>

  <!-- PLATFORM NOTES -->
  <div class="section">
    <div class="section-label">Platform Notes</div>
    <h2>iOS vs Android</h2>
    <div class="platform-row">
      <div class="platform-card">
        <div class="platform-name ios">🍎 iOS</div>
        <ul>
          <li><code>startOrigin</code> is optional — resolved at runtime via location permission</li>
          <li>Full-screen uses a native configurable overlay bottom sheet for trip + maneuver + actions</li>
          <li>Embedded supports custom React overlay content via <code>renderBottomSheet</code></li>
        </ul>
      </div>
      <div class="platform-card">
        <div class="platform-name and">🤖 Android</div>
        <ul>
          <li><code>startOrigin</code> is optional — falls back to current location</li>
          <li>Embedded view uses the real Drop-In <code>NavigationView</code></li>
          <li><code>showsContinuousAlternatives</code> aliases into <code>routeAlternatives</code></li>
        </ul>
      </div>
    </div>
  </div>

  <!-- FEATURE MATRIX -->
  <div class="section">
    <div class="section-label">Feature Parity</div>
    <h2>Platform Matrix</h2>
    <div class="matrix-wrap">
      <table>
        <thead>
          <tr>
            <th>Feature</th>
            <th>iOS Full</th>
            <th>iOS Embed</th>
            <th>Android Full</th>
            <th>Android Embed</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>destination + waypoints routing</td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
          </tr>
          <tr>
            <td>shouldSimulateRoute</td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
          </tr>
          <tr>
            <td>uiTheme / mapStyleUri</td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
          </tr>
          <tr>
            <td>routeAlternatives</td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
          </tr>
          <tr>
            <td>cameraMode / pitch / zoom</td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-partial">Partial</span></td>
            <td><span class="status status-partial">Partial</span></td>
          </tr>
          <tr>
            <td>distanceUnit / language</td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-partial">Partial</span></td>
            <td><span class="status status-partial">Partial</span></td>
          </tr>
          <tr>
            <td>bottomSheet</td>
            <td><span class="status status-partial">Partial</span></td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
          </tr>
          <tr>
            <td>showsSpeedLimits</td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
          </tr>
          <tr>
            <td>showsWayNameLabel</td>
            <td><span class="status status-noop">No-op</span></td>
            <td><span class="status status-noop">No-op</span></td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
          </tr>
          <tr>
            <td>Location / progress / banner events</td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
          </tr>
          <tr>
            <td>Destination preview / changed events</td>
            <td><span class="status status-na">N/A</span></td>
            <td><span class="status status-na">N/A</span></td>
            <td><span class="status status-yes">Yes</span></td>
            <td><span class="status status-yes">Yes</span></td>
          </tr>
          <tr>
            <td>showsReportFeedback</td>
            <td><span class="status status-noop">No-op</span></td>
            <td><span class="status status-noop">No-op</span></td>
            <td><span class="status status-noop">No-op</span></td>
            <td><span class="status status-noop">No-op</span></td>
          </tr>
          <tr>
            <td>routeLineTracksTraversal</td>
            <td><span class="status status-noop">No-op</span></td>
            <td><span class="status status-noop">No-op</span></td>
            <td><span class="status status-noop">No-op</span></td>
            <td><span class="status status-noop">No-op</span></td>
          </tr>
          <tr>
            <td>androidActionButtons.*</td>
            <td><span class="status status-na">N/A</span></td>
            <td><span class="status status-na">N/A</span></td>
            <td><span class="status status-noop">No-op</span></td>
            <td><span class="status status-noop">No-op</span></td>
          </tr>
        </tbody>
      </table>
    </div>
    <p style="margin-top:12px; font-size:12px;">
      <span class="status status-yes" style="margin-right:8px;">Yes</span> Implemented &nbsp;
      <span class="status status-partial" style="margin-right:8px;">Partial</span> Implemented with caveats &nbsp;
      <span class="status status-noop" style="margin-right:8px;">No-op</span> Accepted but ignored &nbsp;
      <span class="status status-na">N/A</span> Not applicable
    </p>
  </div>

  <hr />

  <!-- SUPPORT -->
  <div class="section">
    <div class="support-banner">
      <h2>Support this project ☕</h2>
      <p>If this package helps your work, consider supporting its development. Your contribution helps maintain, improve, and expand the project.</p>
      <a class="kofi-btn" href="https://ko-fi.com/atomiqlabs" target="_blank">
        ♥ &nbsp;Support on Ko-fi
      </a>
    </div>
  </div>

</div>
</body>
</html>
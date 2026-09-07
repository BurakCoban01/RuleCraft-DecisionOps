import { useEffect, useState } from 'react';
import { usePlatformManifest } from './usePlatformManifest';

type Theme = 'light' | 'dark';

function BrandMark() {
  return (
    <svg className="brand-mark" viewBox="0 0 40 40" aria-hidden="true">
      <path d="M7 8.5h14.5L33 20 21.5 31.5H7V8.5Z" />
      <path d="M13 14h8l6 6-6 6h-8V14Z" />
      <circle cx="18" cy="20" r="2.25" />
    </svg>
  );
}

function ThemeIcon({ theme }: { theme: Theme }) {
  return theme === 'light' ? (
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3v2m0 14v2m9-9h-2M5 12H3m15.36-6.36-1.42 1.42M7.06 16.94l-1.42 1.42m12.72 0-1.42-1.42M7.06 7.06 5.64 5.64M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z" /></svg>
  ) : (
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 15.2A8 8 0 0 1 8.8 4 8 8 0 1 0 20 15.2Z" /></svg>
  );
}

function LoadingState() {
  return (
    <section className="state-panel" aria-live="polite" aria-busy="true">
      <div className="state-symbol loading-symbol" aria-hidden="true"><span /></div>
      <p className="eyebrow">Opening workspace</p>
      <h1>Checking your private decision environment</h1>
      <p className="state-copy">Confirming that the workspace is available before showing governed decision content.</p>
      <div className="skeleton-lines" aria-hidden="true"><span /><span /><span /></div>
    </section>
  );
}

function UnavailableState({ offline, retry }: { offline: boolean; retry: () => void }) {
  return (
    <section className="state-panel" role="alert">
      <div className="state-symbol error-symbol" aria-hidden="true">!</div>
      <p className="eyebrow">{offline ? 'Connection interrupted' : 'Workspace unavailable'}</p>
      <h1>{offline ? 'Your workspace is offline' : 'The decision workspace cannot be reached'}</h1>
      <p className="state-copy">
        {offline
          ? 'Your work remains unchanged. Reconnect to continue into the private workspace.'
          : 'The service may be starting or temporarily unavailable. Try the connection again.'}
      </p>
      <button className="primary-button" type="button" onClick={retry}>Try connection again</button>
    </section>
  );
}

function ReadyState({ version, serverTime }: { version: string; serverTime: string }) {
  const time = new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(serverTime));

  return (
    <section className="ready-layout" aria-labelledby="workspace-title">
      <div className="decision-line" aria-hidden="true"><span /><span /><span /></div>
      <div className="ready-copy">
        <div className="status-line" role="status"><span className="status-dot" />Private workspace available</div>
        <p className="eyebrow">Decision operations</p>
        <h1 id="workspace-title">Decision workspace</h1>
        <p className="lede">Author, review, publish, and trace business decisions within the controls assigned to your role.</p>
      </div>
      <aside className="access-panel" aria-labelledby="access-title">
        <div className="access-index" aria-hidden="true">01</div>
        <div>
          <p className="eyebrow">Workspace access</p>
          <h2 id="access-title">Sign-in is required to view decision services</h2>
          <p>Ask your workspace administrator for access. Your role will determine which decisions and actions are available.</p>
        </div>
      </aside>
      <dl className="service-facts" aria-label="Workspace connection details">
        <div><dt>Service state</dt><dd><span className="fact-indicator" />Connected</dd></div>
        <div><dt>Workspace version</dt><dd>{version}</dd></div>
        <div><dt>Confirmed</dt><dd>{time}</dd></div>
      </dl>
    </section>
  );
}

export function App() {
  const { state, refresh } = usePlatformManifest();
  const [theme, setTheme] = useState<Theme>(() => {
    const stored = localStorage.getItem('rulecraft-theme');
    if (stored === 'light' || stored === 'dark') return stored;
    return matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  });

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('rulecraft-theme', theme);
  }, [theme]);

  return (
    <div className="app-shell">
      <header className="mobile-header">
        <a className="brand" href="/" aria-label="RuleCraft home"><BrandMark /><span>RuleCraft</span></a>
        <button className="icon-button" type="button" onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')} aria-label={`Use ${theme === 'light' ? 'dark' : 'light'} theme`}><ThemeIcon theme={theme} /></button>
      </header>
      <aside className="navigation-rail" aria-label="Primary navigation">
        <a className="brand" href="/" aria-label="RuleCraft home"><BrandMark /><span>RuleCraft</span></a>
        <nav>
          <a className="nav-item current" href="/" aria-current="page"><span className="nav-glyph" aria-hidden="true">R</span><span>Workspace</span></a>
        </nav>
        <div className="rail-footer">
          <span className="private-label"><span />Private environment</span>
          <button className="theme-button" type="button" onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}><ThemeIcon theme={theme} /><span>Use {theme === 'light' ? 'dark' : 'light'} theme</span></button>
        </div>
      </aside>
      <main>
        <div className="page-context"><span>RuleCraft</span><span aria-hidden="true">/</span><strong>Workspace</strong></div>
        {state.status === 'loading' && <LoadingState />}
        {state.status === 'offline' && <UnavailableState offline retry={refresh} />}
        {state.status === 'error' && <UnavailableState offline={false} retry={refresh} />}
        {state.status === 'ready' && <ReadyState version={state.manifest.version} serverTime={state.manifest.serverTime} />}
      </main>
      <footer className="product-footer"><span>RuleCraft DecisionOps</span><span>Private workspace</span></footer>
    </div>
  );
}

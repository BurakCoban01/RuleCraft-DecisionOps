import { useCallback, useEffect, useRef, useState } from 'react';
import { loadPlatformManifest, type PlatformManifest } from './platform';

type ConnectionState =
  | { status: 'loading' }
  | { status: 'ready'; manifest: PlatformManifest }
  | { status: 'offline' }
  | { status: 'error' };

export function usePlatformManifest() {
  const [state, setState] = useState<ConnectionState>({ status: 'loading' });
  const requestId = useRef(0);

  const refresh = useCallback(() => {
    if (!navigator.onLine) {
      setState({ status: 'offline' });
      return () => undefined;
    }

    const activeRequest = ++requestId.current;
    const controller = new AbortController();
    setState({ status: 'loading' });
    void loadPlatformManifest(controller.signal)
      .then((manifest) => {
        if (requestId.current === activeRequest) setState({ status: 'ready', manifest });
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        if (requestId.current === activeRequest) {
          setState({ status: navigator.onLine ? 'error' : 'offline' });
        }
      });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    let cancelRequest: () => void = () => undefined;
    const initialRequest = window.setTimeout(() => {
      cancelRequest = refresh();
    }, 0);
    return () => {
      window.clearTimeout(initialRequest);
      cancelRequest();
    };
  }, [refresh]);
  useEffect(() => {
    const handleOnline = () => refresh();
    const handleOffline = () => setState({ status: 'offline' });
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, [refresh]);

  return { state, refresh };
}

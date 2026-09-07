export interface PlatformManifest {
  product: string;
  version: string;
  status: 'ready';
  serverTime: string;
}

export class PlatformRequestError extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
    this.name = 'PlatformRequestError';
  }
}

export async function loadPlatformManifest(signal?: AbortSignal): Promise<PlatformManifest> {
  let response: Response;
  try {
    response = await fetch('/api/v1/platform', {
      headers: { Accept: 'application/json' },
      cache: 'no-store',
      signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new PlatformRequestError('The workspace service could not be reached.');
  }

  if (!response.ok) {
    throw new PlatformRequestError('The workspace service is not available.', response.status);
  }

  const payload = (await response.json()) as Partial<PlatformManifest>;
  if (
    payload.product !== 'RuleCraft DecisionOps' ||
    payload.status !== 'ready' ||
    typeof payload.version !== 'string' ||
    typeof payload.serverTime !== 'string'
  ) {
    throw new PlatformRequestError('The workspace returned an unexpected response.');
  }
  return payload as PlatformManifest;
}

export interface ApiError {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  fieldErrors?: Record<string, string>;
}

function isApiError(value: unknown): value is ApiError {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Record<string, unknown>;

  return (
    typeof v.message === 'string' ||
    typeof v.error === 'string' ||
    (typeof v.fieldErrors === 'object' && v.fieldErrors !== null)
  );
}

function formatFieldErrors(fieldErrors: Record<string, string> | undefined): string | null {
  if (!fieldErrors) return null;

  const entries = Object.entries(fieldErrors).filter(
    ([field, msg]) => field && typeof msg === 'string' && msg.trim()
  );

  if (entries.length === 0) return null;

  return entries
    .map(([field, msg]) => `${field}: ${msg}`)
    .join("; ");
}

export async function readApiError(res: Response): Promise<string> {
  const contentType = res.headers.get('content-type') ?? '';

  try {
    if (contentType.includes('application/json')) {
      const data: unknown = await res.json();

      if (isApiError(data)) {
        const fieldErrorText = formatFieldErrors(data.fieldErrors);

        if (fieldErrorText) {
          return fieldErrorText;
        }

        return data.message ?? data.error ?? `${res.status} ${res.statusText}`;
      }

      return JSON.stringify(data);
    }

    const text = await res.text();
    return text || `${res.status} ${res.statusText}`;
  } catch {
    return `${res.status} ${res.statusText}`;
  }
}
/** Only a hotel detail and known, non-sensitive search criteria may be returned to. */
export function bookingReturn(value: string | null): string | null {
  if (!value || !/^\/hotels\/[a-zA-Z0-9-]+(?:\?|$)/.test(value)) return null;
  try {
    const url = new URL(value, 'https://internal.invalid');
    if (url.origin !== 'https://internal.invalid' || !/^\/hotels\/[a-zA-Z0-9-]+$/.test(url.pathname) || url.hash) return null;
    const params = new URLSearchParams();
    for (const key of ['name', 'city', 'page', 'checkIn', 'checkOut', 'guests']) {
      const item = url.searchParams.get(key); if (item !== null) params.set(key, item);
    }
    return url.pathname + (params.size ? '?' + params.toString() : '');
  } catch { return null; }
}

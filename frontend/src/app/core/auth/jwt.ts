import { AuthRole, JwtClaims } from '../models/auth';

export function isAuthRole(value: unknown): value is AuthRole {
  return value === 'USER' || value === 'HOTEL_ADMIN' || value === 'SUPER_ADMIN';
}

// Structural checks only. Signature verification belongs to Spring Security.
export function readJwt(token: unknown, now = Date.now()): JwtClaims | null {
  if (typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 3 || parts.some(part => !/^[A-Za-z0-9_-]+$/.test(part))) return null;
  try {
    // Verify Base64URL encoding of every segment, including the opaque signature.
    for (const part of parts) {
      const encoded = part.replace(/-/g, '+').replace(/_/g, '/');
      atob(encoded.padEnd(Math.ceil(encoded.length / 4) * 4, '='));
    }
    const decode = (part: string): unknown => {
      const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
      const bytes = Uint8Array.from(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')), c => c.charCodeAt(0));
      return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes)) as unknown;
    };
    const header = decode(parts[0]);
    const value = decode(parts[1]);
    if (!header || typeof header !== 'object' || !('alg' in header) || header.alg !== 'HS256') return null;
    if (!value || typeof value !== 'object') return null;
    if (!('sub' in value) || typeof value.sub !== 'string' || !value.sub.trim()
      || !('userId' in value) || typeof value.userId !== 'string' || !value.userId.trim()
      || !('role' in value) || !isAuthRole(value.role)
      || !('iat' in value) || typeof value.iat !== 'number' || !Number.isSafeInteger(value.iat) || value.iat < 0
      || !('exp' in value) || typeof value.exp !== 'number' || !Number.isSafeInteger(value.exp)
      || value.exp <= value.iat || value.exp * 1000 <= now) return null;
    return { sub: value.sub, userId: value.userId, role: value.role, iat: value.iat, exp: value.exp };
  } catch { return null; }
}

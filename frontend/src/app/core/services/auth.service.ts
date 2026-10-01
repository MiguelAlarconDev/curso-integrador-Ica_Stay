import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs';
import { API_BASE_URL } from '../api.config';
import { AuthRole, AuthSession, LoginRequest, LoginResponse } from '../models/auth';
import { readJwt } from '../auth/jwt';

export const AUTH_STORAGE_KEY = 'icastay.auth.token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_BASE_URL);
  private readonly state = signal<AuthSession | null>(null);
  private expiryTimer?: ReturnType<typeof setTimeout>;
  readonly session = this.state.asReadonly();
  readonly isAuthenticated = computed(() => this.state() !== null);
  readonly email = computed(() => this.state()?.email ?? null);
  readonly userId = computed(() => this.state()?.userId ?? null);
  readonly role = computed(() => this.state()?.role ?? null);

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.expiryTimer));
    let token: string | null = null;
    try { token = sessionStorage.getItem(AUTH_STORAGE_KEY); } catch { /* Storage may be unavailable. */ }
    if (token !== null) {
      if (!this.acceptToken(token)) this.logout();
    }
  }

  login(request: LoginRequest) {
    return this.http.post<LoginResponse>(`${this.api}/api/v1/auth/login`, request).pipe(
      tap(response => {
        if (response?.tokenType !== 'Bearer' || !this.acceptToken(response.token)) {
          throw new Error('Invalid authentication response');
        }
        try { sessionStorage.setItem(AUTH_STORAGE_KEY, response.token); } catch { /* Keep the session in memory. */ }
      }),
    );
  }

  token(): string | null {
    const session = this.state();
    if (session && session.expiresAt * 1000 <= Date.now()) this.logout();
    return this.state()?.token ?? null;
  }

  hasRole(role: AuthRole): boolean { return this.token() !== null && this.role() === role; }

  logout(): void {
    clearTimeout(this.expiryTimer);
    this.state.set(null);
    try { sessionStorage.removeItem(AUTH_STORAGE_KEY); } catch { /* No persistent session to remove. */ }
  }

  private acceptToken(token: string): boolean {
    const claims = readJwt(token);
    if (!claims) return false;
    clearTimeout(this.expiryTimer);
    this.state.set(Object.freeze({ token, email: claims.sub, userId: claims.userId,
      role: claims.role, issuedAt: claims.iat, expiresAt: claims.exp }));
    this.scheduleExpiry();
    return true;
  }

  private scheduleExpiry(): void {
    const remaining = (this.state()?.expiresAt ?? 0) * 1000 - Date.now();
    if (remaining <= 0) { this.logout(); return; }
    this.expiryTimer = setTimeout(() => this.scheduleExpiry(), Math.min(remaining, 2_147_483_647));
  }
}

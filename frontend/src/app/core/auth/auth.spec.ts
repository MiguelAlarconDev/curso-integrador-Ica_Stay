import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { vi } from 'vitest';
import { readJwt } from './jwt';
import { AuthService, AUTH_STORAGE_KEY } from '../services/auth.service';
import { authInterceptor } from '../interceptors/auth.interceptor';
import { authGuard } from '../guards/auth.guard';
import { API_BASE_URL } from '../api.config';

// Deliberately unsigned test fixtures: never used outside unit tests.
function token(changes: Record<string, unknown> = {}): string {
  const now = Math.floor(Date.now() / 1000);
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'user@example.com', userId: '11111111-1111-1111-1111-111111111111', role: 'USER', iat: now, exp: now + 3600, ...changes })}.c2lnbmF0dXJl`;
}

describe('JWT structure', () => {
  it('reads valid claims', () => expect(readJwt(token())?.role).toBe('USER'));
  it.each(['USER', 'HOTEL_ADMIN', 'SUPER_ADMIN'])('accepts official role %s', role => {
    expect(readJwt(token({ role }))?.role).toBe(role);
  });
  it('rejects expiry at the current second', () => expect(readJwt(token({ exp: Math.floor(Date.now() / 1000) }))).toBeNull());
  it.each(['sub', 'userId', 'role', 'iat', 'exp'])('rejects missing %s', claim => {
    expect(readJwt(token({ [claim]: undefined }))).toBeNull();
  });
  it.each(['', 'not-a-token', 'abc.%%%.sig', 'e30.e30.sig', 'a.b.c.d'])('rejects malformed token %s', value => {
    expect(readJwt(value)).toBeNull();
  });
  it('rejects unknown roles', () => expect(readJwt(token({ role: 'ADMIN' }))).toBeNull());
  it('rejects wrongly typed expiration', () => expect(readJwt(token({ exp: '9999999999' }))).toBeNull());
});

describe('Authentication integration', () => {
  const api = 'https://api.example.test';
  let http: HttpTestingController;
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]),
      provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(),
      { provide: API_BASE_URL, useValue: api },
    ] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); TestBed.resetTestingModule(); sessionStorage.clear(); vi.useRealTimers(); });
  function restore(value = token()) {
    sessionStorage.setItem(AUTH_STORAGE_KEY, value);
    return TestBed.inject(AuthService);
  }
  function login(value = token()) {
    const auth = TestBed.inject(AuthService);
    auth.login({ email: 'user@example.com', password: 'test-only' }).subscribe();
    http.expectOne(`${api}/api/v1/auth/login`).flush({ token: value, tokenType: 'Bearer' });
    return auth;
  }
  it('posts the DTO, stores only JWT and exposes session', () => {
    const value = token();
    const auth = TestBed.inject(AuthService);
    auth.login({ email: 'user@example.com', password: 'test-only' }).subscribe();
    const request = http.expectOne(`${api}/api/v1/auth/login`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'user@example.com', password: 'test-only' });
    request.flush({ token: value, tokenType: 'Bearer' });
    expect(auth.email()).toBe('user@example.com');
    expect(auth.hasRole('USER')).toBe(true);
    expect(auth.hasRole('HOTEL_ADMIN')).toBe(false);
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBe(value);
    expect(sessionStorage.length).toBe(1);
  });
  it('restores an existing session', () => expect(restore().isAuthenticated()).toBe(true));
  it.each(['broken', token({ exp: 1 })])('removes invalid stored tokens', value => {
    expect(restore(value).session()).toBeNull();
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull();
  });
  it('logs out locally', () => {
    const auth = restore(); auth.logout();
    expect(auth.session()).toBeNull(); expect(auth.token()).toBeNull();
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull();
  });
  it('expires the session while the page remains open', () => {
    vi.useFakeTimers();
    const now = Math.floor(Date.now() / 1000);
    const auth = restore(token({ exp: now + 2 }));
    vi.advanceTimersByTime(2100);
    expect(auth.session()).toBeNull();
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull();
  });
  it('rejects a malformed successful response without storing it', () => {
    const auth = TestBed.inject(AuthService);
    let failed = false;
    auth.login({ email: 'user@example.com', password: 'test-only' }).subscribe({ error: () => failed = true });
    http.expectOne(`${api}/api/v1/auth/login`).flush({ token: 'broken', tokenType: 'Bearer' });
    expect(failed).toBe(true); expect(auth.session()).toBeNull();
    expect(sessionStorage.length).toBe(0);
  });
  it('keeps an existing session on a login 401', () => {
    const auth = restore(); const current = auth.token();
    auth.login({ email: 'user@example.com', password: 'wrong' }).subscribe({ error: () => {} });
    const request = http.expectOne(`${api}/api/v1/auth/login`);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.token()).toBe(current);
  });
  it('does not attach a header without a session', () => {
    TestBed.inject(HttpClient).get(`${api}/api/v1/hotels`).subscribe();
    const request = http.expectOne(`${api}/api/v1/hotels`);
    expect(request.request.headers.has('Authorization')).toBe(false); request.flush({});
  });
  it('attaches Bearer to the API without cookie credentials', () => {
    const auth = restore();
    TestBed.inject(HttpClient).get(`${api}/api/v1/hotels`).subscribe();
    const request = http.expectOne(`${api}/api/v1/hotels`);
    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${auth.token()}`);
    expect(request.request.withCredentials).toBe(false); request.flush({});
  });
  it.each([`${api}/images/home/hero.webp`, '/assets/test.json', 'https://external.example/api/v1/hotels',
    `${api}.external.example/api/v1/hotels`, `${api}/api/v1/auth/login?test=1`, `${api}/api/v1/auth/login/`])('excludes %s', url => {
    restore(); TestBed.inject(HttpClient).get(url).subscribe();
    const request = http.expectOne(url);
    expect(request.request.headers.has('Authorization')).toBe(false); request.flush({});
  });
  it.each([401, 403])('handles authenticated status %s', status => {
    const auth = restore();
    TestBed.inject(HttpClient).post(`${api}/api/v1/reservations`, {}).subscribe({ error: () => {} });
    http.expectOne(`${api}/api/v1/reservations`).flush({}, { status, statusText: 'Rejected' });
    expect(auth.isAuthenticated()).toBe(status === 403);
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY) !== null).toBe(status === 403);
  });
  it('does not clear a new token because an older request returned 401', () => {
    const auth = restore();
    TestBed.inject(HttpClient).get(`${api}/api/v1/hotels`).subscribe({ error: () => {} });
    const old = http.expectOne(`${api}/api/v1/hotels`);
    login(token({ sub: 'other@example.com' }));
    old.flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.email()).toBe('other@example.com');
  });
  function guard(roles?: string[]) {
    const route = new ActivatedRouteSnapshot(); route.data = roles ? { roles } : {};
    return TestBed.runInInjectionContext(() => authGuard(route, {} as RouterStateSnapshot));
  }
  it('redirects unauthenticated navigation to login', () => {
    expect(TestBed.inject(Router).serializeUrl(guard() as UrlTree)).toBe('/login');
  });
  it('allows an authenticated route', () => { restore(); expect(guard()).toBe(true); });
  it('allows an authorized role', () => { restore(); expect(guard(['USER'])).toBe(true); });
  it('redirects a wrong role home without clearing session', () => {
    const auth = restore();
    expect(TestBed.inject(Router).serializeUrl(guard(['HOTEL_ADMIN']) as UrlTree)).toBe('/');
    expect(auth.isAuthenticated()).toBe(true);
  });
});

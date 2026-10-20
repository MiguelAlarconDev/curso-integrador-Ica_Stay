import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AUTH_STORAGE_KEY, AuthService } from '../../core/services/auth.service';
import { authInterceptor } from '../../core/interceptors/auth.interceptor';
import { DashboardView } from '../../core/models/dashboard';

// Unsigned frontend fixture; server tests use signed JWTs.
function token(role: string): string {
  const now = Math.floor(Date.now() / 1000);
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS256' })}.${encode({ sub: 'admin@example.test', userId: 'test-admin', role, iat: now, exp: now + 3600 })}.c2ln`;
}

const data: DashboardView = {
  hotels: 12, rooms: 45, users: 80,
  reservations: { total: 20, pendingPayment: 7, pendingCurrent: 3, pendingExpired: 4, confirmed: 8, cancelled: 2, expired: 3 },
  paymentAttempts: { APPROVED: 8, REJECTED: 5 }, generatedAt: '2030-01-01T12:00:00Z',
};

describe('SUPER_ADMIN dashboard', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes),
      provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });
  const request = () => TestBed.inject(HttpTestingController).expectOne(r => r.url.endsWith('/api/v1/superadmin/dashboard'));
  async function open() {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token('SUPER_ADMIN'));
    return RouterTestingHarness.create('/superadmin/dashboard');
  }
  function flushCatalog() {
    TestBed.inject(HttpTestingController).match(r => r.url.endsWith('/api/v1/hotels'))
      .forEach(r => r.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
  }

  it('shows loading then actual statistics and labels simulated attempts', async () => {
    const harness = await open();
    expect(harness.routeNativeElement!.textContent).toContain('Cargando estadísticas');
    const pending = request();
    expect(pending.request.headers.get('Authorization')).toBe(`Bearer ${TestBed.inject(AuthService).token()}`);
    pending.flush(data); harness.detectChanges();
    const page = harness.routeNativeElement!;
    expect(page.querySelectorAll('header').length).toBe(1);
    expect(Array.from(page.querySelectorAll('strong')).map(el => el.textContent?.trim()))
      .toEqual(['12', '45', '80', '20', '7', '8', '2', '3', '8', '5']);
    expect(page.textContent).toContain('Vigentes: 3');
    expect(page.textContent).toContain('Vencidas: 4');
    expect(page.textContent).toContain('no ingresos ni cobros reales');
    expect(page.querySelector('main')?.getAttribute('aria-busy')).toBe('false');
  });

  it.each([0, 403, 500])('handles error %s and retries successfully', async status => {
    const harness = await open();
    const pending = request();
    if (status === 0) pending.error(new ProgressEvent('error'));
    else pending.flush({ detail: 'PRIVATE' }, { status, statusText: 'Failure' });
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).not.toContain('PRIVATE');
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')).not.toBeNull();
    harness.routeNativeElement!.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    request().flush(data); harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')).toBeNull();
    expect(harness.routeNativeElement!.textContent).toContain('Confirmadas');
  });

  it('renders zero counts and the empty state', async () => {
    const harness = await open();
    request().flush({ hotels: 0, rooms: 0, users: 0,
      reservations: { total: 0, pendingPayment: 0, pendingCurrent: 0, pendingExpired: 0, confirmed: 0, cancelled: 0, expired: 0 },
      paymentAttempts: { APPROVED: 0, REJECTED: 0 }, generatedAt: data.generatedAt });
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('Todavía no hay datos');
    harness.routeNativeElement!.querySelectorAll('strong').forEach(el => expect(el.textContent?.trim()).toBe('0'));
  });

  it.each(['USER', 'HOTEL_ADMIN'])('blocks %s without requesting global data or removing its session', async role => {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token(role));
    await RouterTestingHarness.create('/superadmin/dashboard');
    expect(TestBed.inject(Router).url).toBe('/');
    flushCatalog();
    TestBed.inject(HttpTestingController).expectNone(r => r.url.endsWith('/api/v1/superadmin/dashboard'));
    expect(TestBed.inject(AuthService).role()).toBe(role);
  });

  it('redirects anonymous access to login', async () => {
    await RouterTestingHarness.create('/superadmin/dashboard');
    expect(TestBed.inject(Router).url).toBe('/login');
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('navigates home and back through the shared header', async () => {
    const harness = await open(); request().flush(data); harness.detectChanges();
    harness.routeNativeElement!.querySelector<HTMLAnchorElement>('a.wordmark')!.click();
    await harness.fixture.whenStable(); flushCatalog(); harness.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/');
    harness.routeNativeElement!.querySelector<HTMLAnchorElement>('a[href="/superadmin/dashboard"]')!.click();
    await harness.fixture.whenStable();
    request().flush(data); harness.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/superadmin/dashboard');
  });

  it('logs out through the shared header and protects subsequent navigation', async () => {
    const harness = await open(); request().flush(data); harness.detectChanges();
    harness.routeNativeElement!.querySelector<HTMLButtonElement>('.session button')!.click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(AuthService).session()).toBeNull();
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/login');
    await harness.navigateByUrl('/superadmin/dashboard');
    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('redirects on authenticated 401', async () => {
    const harness = await open();
    request().flush({}, { status: 401, statusText: 'Unauthorized' });
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/login');
  });
});

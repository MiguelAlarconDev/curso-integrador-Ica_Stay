import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AUTH_STORAGE_KEY, AuthService } from '../../core/services/auth.service';
import { authInterceptor } from '../../core/interceptors/auth.interceptor';
import { SuperadminUserPage } from '../../core/models/superadmin-user';

function token(role: string): string {
  const now = Math.floor(Date.now() / 1000);
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS256' })}.${encode({ sub: 'admin@example.test', userId: 'test-admin', role, iat: now, exp: now + 3600 })}.c2ln`;
}
const data: SuperadminUserPage = {
  content: [{ id: 'user-test', name: 'Hotel Valle', email: 'admin@example.test', role: 'HOTEL_ADMIN', status: 'INACTIVE' }],
  page: 0, size: 20, totalElements: 21, totalPages: 2,
};

describe('SUPER_ADMIN user listing', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes),
      provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });
  const request = () => TestBed.inject(HttpTestingController).expectOne(r => r.url.endsWith('/api/v1/superadmin/users'));
  async function open(query = '') {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token('SUPER_ADMIN'));
    return RouterTestingHarness.create('/superadmin/users' + query);
  }
  function fill(harness: RouterTestingHarness, name: string, status: string) {
    const input = harness.routeNativeElement!.querySelector<HTMLInputElement>('#user-q')!;
    input.value = name; input.dispatchEvent(new Event('input'));
    const select = harness.routeNativeElement!.querySelector<HTMLSelectElement>('#user-status')!;
    const role = harness.routeNativeElement!.querySelector<HTMLSelectElement>('#user-role')!; role.value = 'HOTEL_ADMIN'; role.dispatchEvent(new Event('change'));
    select.value = status; select.dispatchEvent(new Event('change')); harness.detectChanges();
  }
  async function click(harness: RouterTestingHarness, selector: string) {
    harness.routeNativeElement!.querySelector<HTMLElement>(selector)!.click();
    await harness.fixture.whenStable(); harness.detectChanges();
  }

  it('loads with JWT and shows inactive hotel, location and administrator', async () => {
    const harness = await open();
    expect(harness.routeNativeElement!.textContent).toContain('Cargando usuarios');
    const pending = request();
    expect(pending.request.method).toBe('GET');
    expect(pending.request.params.get('page')).toBe('0');
    expect(pending.request.headers.get('Authorization')).toBe(`Bearer ${TestBed.inject(AuthService).token()}`);
    pending.flush(data); harness.detectChanges();
    const page = harness.routeNativeElement!;
    for (const text of ['Hotel Valle', 'Inactivo', 'admin@example.test', 'HOTEL_ADMIN']) {
      expect(page.textContent).toContain(text);
    }
    expect(page.querySelector<HTMLButtonElement>('.previous')!.disabled).toBe(true);
    expect(page.querySelector<HTMLButtonElement>('.next')!.disabled).toBe(false);
  });

  it('applies filters from page two, resets page, paginates and clears', async () => {
    const harness = await open('?page=1'); request().flush({ ...data, page: 1 }); harness.detectChanges();
    fill(harness, '  Valle  ', 'INACTIVE');
    await click(harness, 'button[type="submit"]');
    let pending = request();
    expect(pending.request.params.get('q')).toBe('Valle');
    expect(pending.request.params.get('role')).toBe('HOTEL_ADMIN');
    expect(pending.request.params.get('status')).toBe('INACTIVE');
    expect(pending.request.params.get('page')).toBe('0');
    pending.flush(data); harness.detectChanges();
    await click(harness, '.next'); pending = request();
    expect(pending.request.params.get('page')).toBe('1');
    expect(pending.request.params.get('q')).toBe('Valle');
    expect(pending.request.params.get('role')).toBe('HOTEL_ADMIN');
    expect(pending.request.params.get('status')).toBe('INACTIVE');
    pending.flush({ ...data, page: 1 }); harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector<HTMLButtonElement>('.next')!.disabled).toBe(true);
    await click(harness, '.previous'); request().flush(data); harness.detectChanges();
    await click(harness, '.clear'); pending = request();
    expect(pending.request.params.get('page')).toBe('0');
    expect(pending.request.params.has('q')).toBe(false);
    expect(pending.request.params.has('status')).toBe(false);
    pending.flush(data); harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector<HTMLInputElement>('#user-q')!.value).toBe('');
    expect(harness.routeNativeElement!.querySelector<HTMLSelectElement>('#user-status')!.value).toBe('');
  });

  it.each([0, 403, 500])('handles error %s and retries the same query', async status => {
    const harness = await open('?q=Valle&status=INACTIVE&page=1');
    const pending = request();
    if (status === 0) pending.error(new ProgressEvent('error'));
    else pending.flush({ detail: 'PRIVATE' }, { status, statusText: 'Failure' });
    harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')).not.toBeNull();
    expect(harness.routeNativeElement!.textContent).not.toContain('PRIVATE');
    await click(harness, '.retry'); const retry = request();
    expect(retry.request.params.get('q')).toBe('Valle');
    expect(retry.request.params.get('status')).toBe('INACTIVE');
    expect(retry.request.params.get('page')).toBe('1');
    retry.flush({ ...data, page: 1 }); harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')).toBeNull();
  });

  it('shows an empty result and recovers from a page beyond the last', async () => {
    const harness = await open('?page=4');
    request().flush({ ...data, content: [], page: 4 }); harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('No hay usuarios');
    expect(harness.routeNativeElement!.querySelector('[aria-label="Paginación de usuarios"]')).toBeNull();
    await click(harness, '.first');
    const pending = request(); expect(pending.request.params.get('page')).toBe('0');
    pending.flush({ ...data, content: [], totalElements: 0, totalPages: 0 }); harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('0 usuarios encontrados');
    expect(harness.routeNativeElement!.querySelector('tbody')).toBeNull();
  });

  it('cancels obsolete requests when filters change', async () => {
    const harness = await open(); const old = request();
    fill(harness, 'Oasis', 'ACTIVE'); await click(harness, 'button[type="submit"]');
    expect(old.cancelled).toBe(true);
    request().flush(data); harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).not.toContain('Cargando usuarios');
  });

  it('rejects invalid URL filters locally and allows clearing', async () => {
    const harness = await open('?status=OTHER');
    TestBed.inject(HttpTestingController).expectNone(r => r.url.endsWith('/api/v1/superadmin/users'));
    expect(harness.routeNativeElement!.textContent).toContain('Revisa los filtros');
    await click(harness, '.clear'); request().flush(data);
  });

  it.each(['USER', 'HOTEL_ADMIN'])('blocks %s without requesting administrative data', async role => {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token(role));
    await RouterTestingHarness.create('/superadmin/users');
    expect(TestBed.inject(Router).url).toBe('/');
    TestBed.inject(HttpTestingController).match(r => r.url.endsWith('/api/v1/hotels'))
      .forEach(r => r.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
    TestBed.inject(HttpTestingController).expectNone(r => r.url.endsWith('/api/v1/superadmin/users'));
    expect(TestBed.inject(AuthService).role()).toBe(role);
  });

  it('redirects anonymous access to login', async () => {
    await RouterTestingHarness.create('/superadmin/users');
    expect(TestBed.inject(Router).url).toBe('/login');
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('navigates from dashboard to hotels and back', async () => {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token('SUPER_ADMIN'));
    const harness = await RouterTestingHarness.create('/superadmin/dashboard');
    TestBed.inject(HttpTestingController).expectOne(r => r.url.endsWith('/api/v1/superadmin/dashboard'))
      .flush({}, { status: 500, statusText: 'Failure' });
    harness.detectChanges();
    await click(harness, 'a[href="/superadmin/users"]'); request().flush(data); harness.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/superadmin/users');
    await click(harness, '.back');
    expect(TestBed.inject(Router).url).toBe('/superadmin/dashboard');
    TestBed.inject(HttpTestingController).expectOne(r => r.url.endsWith('/api/v1/superadmin/dashboard'))
      .flush({}, { status: 500, statusText: 'Failure' });
  });

  it('cancels pending requests when navigating away', async () => {
    const harness = await open(); const pending = request();
    await harness.navigateByUrl('/login'); expect(pending.cancelled).toBe(true);
  });

  it('logs out through the shared header and leaves the protected listing', async () => {
    const harness = await open(); request().flush(data); harness.detectChanges();
    await click(harness, '.session button');
    expect(TestBed.inject(AuthService).session()).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/login');
  });
});

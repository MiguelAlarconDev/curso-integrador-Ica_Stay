import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AUTH_STORAGE_KEY, AuthService } from '../../core/services/auth.service';
import { authInterceptor } from '../../core/interceptors/auth.interceptor';
import { SuperadminHotelPage } from '../../core/models/superadmin-hotel';

function token(role: string): string {
  const now = Math.floor(Date.now() / 1000);
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS256' })}.${encode({ sub: 'admin@example.test', userId: 'test-admin', role, iat: now, exp: now + 3600 })}.c2ln`;
}
const data: SuperadminHotelPage = {
  content: [{ id: 'hotel-test', name: 'Hotel Valle', address: 'Calle 123', city: 'Ica', status: 'INACTIVE',
    adminUserId: 'admin-test', adminName: 'Administrador de prueba', adminEmail: 'admin@example.test' }],
  page: 0, size: 20, totalElements: 21, totalPages: 2,
};

describe('SUPER_ADMIN hotel listing', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes),
      provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });
  const request = () => TestBed.inject(HttpTestingController).expectOne(r => r.url.endsWith('/api/v1/superadmin/hotels'));
  async function open(query = '') {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token('SUPER_ADMIN'));
    return RouterTestingHarness.create('/superadmin/hotels' + query);
  }
  function fill(harness: RouterTestingHarness, name: string, status: string) {
    const input = harness.routeNativeElement!.querySelector<HTMLInputElement>('#hotel-name')!;
    input.value = name; input.dispatchEvent(new Event('input'));
    const select = harness.routeNativeElement!.querySelector<HTMLSelectElement>('#hotel-status')!;
    select.value = status; select.dispatchEvent(new Event('change')); harness.detectChanges();
  }
  async function click(harness: RouterTestingHarness, selector: string) {
    harness.routeNativeElement!.querySelector<HTMLElement>(selector)!.click();
    await harness.fixture.whenStable(); harness.detectChanges();
  }

  it('loads with JWT and shows inactive hotel, location and administrator', async () => {
    const harness = await open();
    expect(harness.routeNativeElement!.textContent).toContain('Cargando hoteles');
    const pending = request();
    expect(pending.request.method).toBe('GET');
    expect(pending.request.params.get('page')).toBe('0');
    expect(pending.request.headers.get('Authorization')).toBe(`Bearer ${TestBed.inject(AuthService).token()}`);
    pending.flush(data); harness.detectChanges();
    const page = harness.routeNativeElement!;
    for (const text of ['Hotel Valle', 'Inactivo', 'Calle 123', 'Ica', 'Administrador de prueba', 'admin@example.test', 'admin-test', 'hotel-test']) {
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
    expect(pending.request.params.get('name')).toBe('Valle');
    expect(pending.request.params.get('status')).toBe('INACTIVE');
    expect(pending.request.params.get('page')).toBe('0');
    pending.flush(data); harness.detectChanges();
    await click(harness, '.next'); pending = request();
    expect(pending.request.params.get('page')).toBe('1');
    expect(pending.request.params.get('name')).toBe('Valle');
    expect(pending.request.params.get('status')).toBe('INACTIVE');
    pending.flush({ ...data, page: 1 }); harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector<HTMLButtonElement>('.next')!.disabled).toBe(true);
    await click(harness, '.previous'); request().flush(data); harness.detectChanges();
    await click(harness, '.clear'); pending = request();
    expect(pending.request.params.get('page')).toBe('0');
    expect(pending.request.params.has('name')).toBe(false);
    expect(pending.request.params.has('status')).toBe(false);
    pending.flush(data); harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector<HTMLInputElement>('#hotel-name')!.value).toBe('');
    expect(harness.routeNativeElement!.querySelector<HTMLSelectElement>('#hotel-status')!.value).toBe('');
  });

  it.each([0, 403, 500])('handles error %s and retries the same query', async status => {
    const harness = await open('?name=Valle&status=INACTIVE&page=1');
    const pending = request();
    if (status === 0) pending.error(new ProgressEvent('error'));
    else pending.flush({ detail: 'PRIVATE' }, { status, statusText: 'Failure' });
    harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')).not.toBeNull();
    expect(harness.routeNativeElement!.textContent).not.toContain('PRIVATE');
    await click(harness, '.retry'); const retry = request();
    expect(retry.request.params.get('name')).toBe('Valle');
    expect(retry.request.params.get('status')).toBe('INACTIVE');
    expect(retry.request.params.get('page')).toBe('1');
    retry.flush({ ...data, page: 1 }); harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')).toBeNull();
  });

  it('shows an empty result and recovers from a page beyond the last', async () => {
    const harness = await open('?page=4');
    request().flush({ ...data, content: [], page: 4 }); harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('No hay hoteles');
    expect(harness.routeNativeElement!.querySelector('[aria-label="Paginación de hoteles"]')).toBeNull();
    await click(harness, '.first');
    const pending = request(); expect(pending.request.params.get('page')).toBe('0');
    pending.flush({ ...data, content: [], totalElements: 0, totalPages: 0 }); harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('0 hoteles encontrados');
    expect(harness.routeNativeElement!.querySelector('article')).toBeNull();
  });

  it('cancels obsolete requests when filters change', async () => {
    const harness = await open(); const old = request();
    fill(harness, 'Oasis', 'ACTIVE'); await click(harness, 'button[type="submit"]');
    expect(old.cancelled).toBe(true);
    request().flush(data); harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).not.toContain('Cargando hoteles');
  });

  it('rejects invalid URL filters locally and allows clearing', async () => {
    const harness = await open('?status=OTHER');
    TestBed.inject(HttpTestingController).expectNone(r => r.url.endsWith('/api/v1/superadmin/hotels'));
    expect(harness.routeNativeElement!.textContent).toContain('Revisa los filtros');
    await click(harness, '.clear'); request().flush(data);
  });

  it.each(['USER', 'HOTEL_ADMIN'])('blocks %s without requesting administrative data', async role => {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token(role));
    await RouterTestingHarness.create('/superadmin/hotels');
    expect(TestBed.inject(Router).url).toBe('/');
    TestBed.inject(HttpTestingController).match(r => r.url.endsWith('/api/v1/hotels'))
      .forEach(r => r.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
    TestBed.inject(HttpTestingController).expectNone(r => r.url.endsWith('/api/v1/superadmin/hotels'));
    expect(TestBed.inject(AuthService).role()).toBe(role);
  });

  it('redirects anonymous access to login', async () => {
    await RouterTestingHarness.create('/superadmin/hotels');
    expect(TestBed.inject(Router).url).toBe('/login');
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('navigates from dashboard to hotels and back', async () => {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token('SUPER_ADMIN'));
    const harness = await RouterTestingHarness.create('/superadmin/dashboard');
    TestBed.inject(HttpTestingController).expectOne(r => r.url.endsWith('/api/v1/superadmin/dashboard'))
      .flush({}, { status: 500, statusText: 'Failure' });
    harness.detectChanges();
    await click(harness, 'a[href="/superadmin/hotels"]'); request().flush(data); harness.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/superadmin/hotels');
    await click(harness, '.back');
    expect(TestBed.inject(Router).url).toBe('/superadmin/dashboard');
    TestBed.inject(HttpTestingController).expectOne(r => r.url.endsWith('/api/v1/superadmin/dashboard'))
      .flush({}, { status: 500, statusText: 'Failure' });
  });

  it('logs out through the shared header and leaves the protected listing', async () => {
    const harness = await open(); request().flush(data); harness.detectChanges();
    await click(harness, '.session button');
    expect(TestBed.inject(AuthService).session()).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/login');
  });
});

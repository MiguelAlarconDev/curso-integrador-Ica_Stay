import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AUTH_STORAGE_KEY, AuthService } from '../../core/services/auth.service';
import { authInterceptor } from '../../core/interceptors/auth.interceptor';
import { UserReservationView } from '../../core/models/reservation';

describe('My reservations', () => {
  const api = 'http://localhost:8080/api/v1/reservations';
  const item: UserReservationView = {
    id: 'own', hotelId: 'hotel', hotelName: 'Hotel del Valle', roomId: 'room', roomNumber: '101',
    checkIn: '2030-01-10', checkOut: '2030-01-12', guests: 2, status: 'PENDING_PAYMENT',
    expiresAt: '2030-01-01T12:00:00Z', createdAt: '2030-01-01T11:45:00Z',
    nightlyPriceSnapshot: 125.5, totalAmount: 251, currency: 'PEN', evaluatedAt: '2030-01-01T12:00:00Z', holdExpired: true,
  };
  const result = { content: [item], page: 0, size: 20, totalElements: 21, totalPages: 2 };
  function session(role = 'USER') {
    const now = Math.floor(Date.now() / 1000);
    const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
    sessionStorage.setItem(AUTH_STORAGE_KEY, `${encode({ alg: 'HS256' })}.${encode({ sub: 'user@example.test', userId: 'user-a', role, iat: now, exp: now + 3600 })}.c2ln`);
  }
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });
  const http = () => TestBed.inject(HttpTestingController);
  const list = () => http().expectOne(request => request.url === `${api}/me`);
  const page = (h: RouterTestingHarness) => h.routeNativeElement!;
  async function click(h: RouterTestingHarness, selector: string) {
    page(h).querySelector<HTMLElement>(selector)!.click(); await h.fixture.whenStable(); h.detectChanges();
  }
  function flushHome() {
    http().match(request => request.url.endsWith('/api/v1/hotels')).forEach(request => request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
  }

  it('loads with Bearer, paginates, opens detail, and returns to the same page', async () => {
    session(); const h = await RouterTestingHarness.create('/my-reservations');
    expect(page(h).textContent).toContain('Cargando reservas');
    const first = list(); expect(first.request.method).toBe('GET'); expect(first.request.params.keys()).toEqual(['page']);
    expect(first.request.headers.get('Authorization')).toBe(`Bearer ${sessionStorage.getItem(AUTH_STORAGE_KEY)}`);
    first.flush(result); h.detectChanges();
    for (const text of ['Hotel del Valle', '101', '2030-01-10', '251.00 PEN', 'PENDING_PAYMENT', 'Retención vencida']) expect(page(h).textContent).toContain(text);
    expect(page(h).querySelector('header a[href="/my-reservations"]')).not.toBeNull();
    await click(h, '.next'); const next = list(); expect(next.request.params.get('page')).toBe('1');
    next.flush({ ...result, page: 1 }); h.detectChanges();
    await click(h, '.detail-link'); const detail = http().expectOne(`${api}/own`); expect(detail.request.headers.has('Authorization')).toBe(true);
    detail.flush(item); h.detectChanges();
    for (const text of ['own', '125.50 PEN', '251.00 PEN', '12:00:00', 'Huéspedes']) expect(page(h).textContent).toContain(text);
    await click(h, '.back'); const back = list(); expect(back.request.params.get('page')).toBe('1'); back.flush({ ...result, page: 1 });
  });

  it('shows an empty page and allows refreshing without duplicate requests', async () => {
    session(); const h = await RouterTestingHarness.create('/my-reservations');
    list().flush({ ...result, content: [], totalElements: 0, totalPages: 0 }); h.detectChanges();
    expect(page(h).textContent).toContain('Todavía no hay reservas');
    const button = page(h).querySelector<HTMLButtonElement>('.refresh')!; button.click(); button.click(); h.detectChanges();
    list().flush(result); h.detectChanges(); expect(page(h).querySelector('.detail-link')).not.toBeNull();
  });

  it.each(['CONFIRMED', 'CANCELLED', 'EXPIRED', 'PENDING_PAYMENT'] as const)('shows persisted %s without inferring expiry locally', async status => {
    session(); const h = await RouterTestingHarness.create('/my-reservations/own');
    http().expectOne(`${api}/own`).flush({ ...item, status, expiresAt: '2000-01-01T00:00:00Z', holdExpired: false }); h.detectChanges();
    expect(page(h).textContent).toContain(status); expect(page(h).textContent).not.toContain('Retención vencida');
  });

  it('uses refreshed server expiry and never writes status', async () => {
    session(); const h = await RouterTestingHarness.create('/my-reservations/own');
    http().expectOne(`${api}/own`).flush({ ...item, holdExpired: false }); h.detectChanges();
    await click(h, '.refresh'); const updated = http().expectOne(`${api}/own`); expect(updated.request.method).toBe('GET');
    updated.flush(item); h.detectChanges(); expect(page(h).textContent).toContain('Retención vencida');
    expect(page(h).textContent).toContain('PENDING_PAYMENT');
  });

  it('cancels obsolete pages, details and requests when leaving the route', async () => {
    session(); const h = await RouterTestingHarness.create('/my-reservations'); const initial = list();
    await h.navigateByUrl('/my-reservations?page=1'); expect(initial.cancelled).toBe(true);
    const pending = list(); await h.navigateByUrl('/my-reservations/old'); expect(pending.cancelled).toBe(true);
    const old = http().expectOne(`${api}/old`); await h.navigateByUrl('/my-reservations/new'); expect(old.cancelled).toBe(true);
    const latest = http().expectOne(`${api}/new`); await h.navigateByUrl('/login'); expect(latest.cancelled).toBe(true);
  });

  for (const detail of [false, true]) {
    it.each([0, 400, 403, 404, 500])(`handles ${detail ? 'detail' : 'list'} HTTP %s with retry`, async status => {
      session(); const h = await RouterTestingHarness.create(detail ? '/my-reservations/own' : '/my-reservations');
      const request = detail ? http().expectOne(`${api}/own`) : list();
      if (status === 0) request.error(new ProgressEvent('error'));
      else request.flush({ message: 'PRIVATE DETAIL' }, { status, statusText: 'Rejected' });
      h.detectChanges(); expect(page(h).querySelector('[role="alert"]')).not.toBeNull(); expect(page(h).textContent).not.toContain('PRIVATE DETAIL');
      if (status === 404) expect(page(h).textContent).toContain('no está disponible para tu usuario');
      expect(TestBed.inject(AuthService).session()).not.toBeNull();
      await click(h, '.retry'); (detail ? http().expectOne(`${api}/own`) : list()).flush(detail ? item : result);
      h.detectChanges(); expect(page(h).querySelector('[role="alert"]')).toBeNull();
    });
  }

  it.each(['/my-reservations', '/my-reservations/own'])('guards anonymous access to %s', async path => {
    await RouterTestingHarness.create(path); expect(TestBed.inject(Router).url).toBe('/login'); http().expectNone(() => true);
  });
  it.each(['HOTEL_ADMIN', 'SUPER_ADMIN'])('blocks role %s on both routes', async role => {
    session(role); const h = await RouterTestingHarness.create();
    for (const path of ['/my-reservations', '/my-reservations/own']) {
      await h.navigateByUrl(path); expect(TestBed.inject(Router).url).toBe('/'); flushHome();
    }
    http().expectNone(request => request.url.startsWith(api));
  });
  it('leaves and clears private data on 401', async () => {
    session(); const h = await RouterTestingHarness.create('/my-reservations');
    list().flush({}, { status: 401, statusText: 'Unauthorized' }); await h.fixture.whenStable(); h.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/login'); expect(TestBed.inject(AuthService).session()).toBeNull();
    expect(page(h).textContent).not.toContain('Hotel del Valle');
  });
  it('leaves private history on logout', async () => {
    session(); const h = await RouterTestingHarness.create('/my-reservations'); list().flush(result); h.detectChanges();
    await click(h, '.session button'); expect(TestBed.inject(Router).url).toBe('/login');
    expect(page(h).textContent).not.toContain('Hotel del Valle');
  });
});

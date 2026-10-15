import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HotelReservations } from './reservations';
import { authInterceptor } from '../../../../core/interceptors/auth.interceptor';
import { AUTH_STORAGE_KEY, AuthService } from '../../../../core/services/auth.service';

describe('Hotel reservations', () => {
  const api = 'http://localhost:8080/api/v1/admin/hotel/reservations';
  const reservation = { id: 'reservation-own', roomId: 'room-own', roomNumber: '101', guestUserId: 'guest-test',
    guestName: 'Huésped de prueba', guestEmail: 'guest@example.test', checkIn: '2026-11-01', checkOut: '2026-11-03',
    guests: 2, status: 'PENDING_PAYMENT', nightlyPriceSnapshot: 120.5, totalAmount: 241, currency: 'PEN',
    createdAt: '2026-10-15T12:00:00Z', expiresAt: '2026-10-15T12:15:00Z' };
  const result = { content: [reservation], page: 0, size: 20, totalElements: 21, totalPages: 2 };
  beforeEach(() => {
    sessionStorage.clear();
    const now = Math.floor(Date.now() / 1000);
    const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
    // Unsigned JWT fixture for HTTP-mocked frontend tests only.
    sessionStorage.setItem(AUTH_STORAGE_KEY, `${encode({ alg: 'HS256' })}.${encode({ sub: 'admin@example.test', userId: 'admin-test', role: 'HOTEL_ADMIN', iat: now, exp: now + 3600 })}.c2ln`);
    TestBed.configureTestingModule({ imports: [HotelReservations], providers: [
      provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(),
    ] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });

  function setup(load = true) {
    const fixture = TestBed.createComponent(HotelReservations); fixture.detectChanges();
    const page: HTMLElement = fixture.nativeElement;
    const http = TestBed.inject(HttpTestingController);
    const list = () => http.expectOne(request => request.url === api);
    if (load) { list().flush(result); fixture.detectChanges(); }
    const click = (selector: string) => { page.querySelector<HTMLButtonElement>(selector)!.click(); fixture.detectChanges(); };
    const fill = (name: string, value: string) => {
      const field = page.querySelector<HTMLInputElement>(`[formControlName="${name}"]`)!;
      field.value = value; field.dispatchEvent(new Event(name === 'status' ? 'change' : 'input')); fixture.detectChanges();
    };
    const apply = () => { page.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true })); fixture.detectChanges(); };
    return { fixture, page, http, list, click, fill, apply };
  }

  it('loads once with Bearer, default pagination and real fields', () => {
    const { fixture, page, http, list } = setup(false);
    expect(page.textContent).toContain('Cargando reservas');
    const request = list();
    expect(request.request.method).toBe('GET');
    expect(request.request.urlWithParams).toBe(`${api}?page=0&size=20`);
    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${sessionStorage.getItem(AUTH_STORAGE_KEY)}`);
    request.flush(result); fixture.detectChanges();
    expect(page.textContent).toContain('Habitación 101'); expect(page.textContent).toContain('2026-11-01');
    expect(page.textContent).toContain('2026-11-03'); expect(page.textContent).toContain('241.00 PEN');
    expect(page.textContent).toContain('Pendiente de pago'); expect(page.textContent).toContain('Página 1 de 2');
    expect(page.querySelector<HTMLButtonElement>('.previous')!.disabled).toBe(true);
    fixture.detectChanges(); http.expectNone(() => true);
  });

  it('shows empty results without detail or pagination controls', () => {
    const { fixture, page, list } = setup(false);
    list().flush({ ...result, content: [], totalElements: 0, totalPages: 0 }); fixture.detectChanges();
    expect(page.textContent).toContain('No hay reservas');
    expect(page.querySelector('.details')).toBeNull(); expect(page.querySelector('.next')).toBeNull();
  });

  it('applies only supported filters, preserves them across pages and clears to page zero', () => {
    const { fixture, page, list, fill, apply, click, http } = setup();
    fill('status', 'CONFIRMED'); fill('from', '2026-11-01'); fill('to', '2026-11-30');
    http.expectNone(() => true); apply(); apply();
    const filtered = list();
    expect(filtered.request.params.keys().sort()).toEqual(['from', 'page', 'size', 'status', 'to']);
    expect(filtered.request.params.get('status')).toBe('CONFIRMED');
    filtered.flush(result); fixture.detectChanges();
    // Unapplied draft edits must not leak into pagination.
    fill('status', 'EXPIRED'); click('.next');
    const next = list(); expect(next.request.params.get('page')).toBe('1');
    expect(next.request.params.get('status')).toBe('CONFIRMED');
    expect(next.request.params.get('from')).toBe('2026-11-01'); expect(next.request.params.get('to')).toBe('2026-11-30');
    next.flush({ ...result, page: 1 }); fixture.detectChanges();
    expect(page.querySelector<HTMLButtonElement>('.next')!.disabled).toBe(true);
    click('form button[type="button"]');
    const cleared = list(); expect(cleared.request.urlWithParams).toBe(`${api}?page=0&size=20`);
    cleared.flush(result); fixture.detectChanges();
    expect(page.querySelector<HTMLInputElement>('[formControlName="from"]')!.value).toBe('');
  });

  it('resets page when applying new filters and rejects reversed dates', () => {
    const { fixture, http, list, click, fill, apply, page } = setup();
    click('.next'); list().flush({ ...result, page: 1 }); fixture.detectChanges();
    fill('from', '2026-12-02'); fill('to', '2026-12-01'); apply(); http.expectNone(() => true);
    expect(page.querySelector('[role="alert"]')).not.toBeNull();
    fill('to', '2026-12-02'); apply(); const request = list();
    expect(request.request.params.get('page')).toBe('0'); request.flush(result);
  });

  it('cancels obsolete lists on filters and detail on filter changes and destruction', () => {
    const { fixture, list, fill, apply, click, http } = setup(false);
    const initial = list(); fill('status', 'CONFIRMED'); apply(); expect(initial.cancelled).toBe(true);
    const filtered = list(); filtered.flush(result); fixture.detectChanges();
    click('.details'); const detail = http.expectOne(`${api}/${reservation.id}`);
    fill('status', 'EXPIRED'); apply(); expect(detail.cancelled).toBe(true);
    const pending = list(); fixture.destroy(); expect(pending.cancelled).toBe(true);
  });

  it('loads independent detail, cancels previous selections, and returns without fetching the list', () => {
    const { fixture, page, list, http, click } = setup(false);
    list().flush({ ...result, content: [reservation, { ...reservation, id: 'second' }] }); fixture.detectChanges();
    click('.details'); const first = http.expectOne(`${api}/${reservation.id}`);
    page.querySelectorAll<HTMLButtonElement>('.details')[1].click(); fixture.detectChanges();
    expect(first.cancelled).toBe(true); expect(page.textContent).toContain('Cargando detalle');
    const second = http.expectOne(`${api}/second`);
    expect(second.request.method).toBe('GET'); expect(second.request.headers.has('Authorization')).toBe(true);
    second.flush({ ...reservation, id: 'second' }); fixture.detectChanges();
    const detail = page.querySelector('#reservation-detail')!.textContent;
    for (const value of ['second', 'guest@example.test', '120.50', '241.00', '2026-10-15 12:15', 'guest-test', 'room-own']) expect(detail).toContain(value);
    click('.back'); expect(page.querySelector('#reservation-detail')!.textContent?.trim()).toBe('');
    http.expectNone(() => true);
  });

  it('omits null expiration without inferring a status from dates', () => {
    const { click, fixture, page, http } = setup(); click('.details');
    http.expectOne(`${api}/${reservation.id}`).flush({ ...reservation, expiresAt: null, status: 'CONFIRMED' }); fixture.detectChanges();
    expect(page.querySelector('#reservation-detail')!.textContent).not.toContain('Expiración');
    expect(page.querySelector('#reservation-detail')!.textContent).toContain('Confirmada');
  });

  for (const detail of [false, true]) {
    it.each([0, 400, 401, 403, 404, 500])(`handles ${detail ? 'detail' : 'list'} HTTP %s and retries`, status => {
      const { fixture, page, http, list, click } = setup(detail);
      if (detail) click('.details');
      const request = detail ? http.expectOne(`${api}/${reservation.id}`) : list();
      if (status === 0) request.error(new ProgressEvent('error'));
      else request.flush({ message: 'PRIVATE DETAIL' }, { status, statusText: 'Rejected' });
      fixture.detectChanges();
      expect(page.querySelector('[role="alert"]')).not.toBeNull(); expect(page.textContent).not.toContain('PRIVATE DETAIL');
      if (status === 404) expect(page.textContent).toContain(detail ? 'no pertenece a tu hotel' : 'No tienes un hotel asociado');
      expect(TestBed.inject(AuthService).session() === null).toBe(status === 401);
      const selector = detail ? '#reservation-detail button:not(.back)' : '.retry';
      const retry = page.querySelector<HTMLButtonElement>(selector)!;
      retry.click(); retry.click(); fixture.detectChanges();
      (detail ? http.expectOne(`${api}/${reservation.id}`) : list()).flush(detail ? reservation : result); fixture.detectChanges();
      expect(page.querySelector('[role="alert"]')).toBeNull();
    });
  }
});

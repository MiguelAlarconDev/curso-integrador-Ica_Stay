import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router, ActivatedRoute, convertToParamMap } from '@angular/router';
import { Booking } from './booking';
import { authInterceptor } from '../../core/interceptors/auth.interceptor';
import { AUTH_STORAGE_KEY } from '../../core/services/auth.service';
import { bookingReturn } from '../../core/auth/booking-return';
import { ReservationService } from '../../core/services/reservation.service';

describe('Booking confirmation', () => {
  const api = 'http://localhost:8080/api/v1/reservations';
  const query = { checkIn: '2030-01-01', checkOut: '2030-01-03', guests: 2 };
  function token(role: string) {
    const now = Math.floor(Date.now() / 1000);
    const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
    return `${encode({ alg: 'HS256' })}.${encode({ sub: 'guest@example.test', userId: 'test', role, iat: now, exp: now + 3600 })}.c2ln`;
  }
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ imports: [Booking], providers: [provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { queryParams: { name: 'Oasis', page: 1 }, queryParamMap: convertToParamMap({}) } } },
      provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });
  function setup(role: string | null = 'USER') {
    if (role) sessionStorage.setItem(AUTH_STORAGE_KEY, token(role));
    const fixture = TestBed.createComponent(Booking);
    fixture.componentRef.setInput('room', { id: 'room', number: '101', description: null, capacity: 2, pricePerNight: 100, currency: 'PEN' });
    fixture.componentRef.setInput('query', query); fixture.componentRef.setInput('hotelId', 'hotel'); fixture.componentRef.setInput('hotelName', 'Hotel Oasis'); fixture.detectChanges();
    const page: HTMLElement = fixture.nativeElement;
    const confirm = () => { page.querySelector<HTMLInputElement>('input[type="checkbox"]')!.click(); fixture.detectChanges(); };
    const submit = () => { page.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true })); fixture.detectChanges(); };
    return { fixture, page, confirm, submit, http: TestBed.inject(HttpTestingController) };
  }
  it('requires confirmation, sends exact payload once and shows authoritative result', () => {
    const { fixture, page, confirm, submit, http } = setup(); submit(); http.expectNone(api);
    expect(page.textContent).toContain('2 noches'); expect(page.textContent).toContain('200.00 PEN');
    confirm(); submit(); submit();
    const request = http.expectOne(api); expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ roomId: 'room', ...query }); expect(request.request.headers.has('Authorization')).toBe(true);
    request.flush({ id: 'reservation-real', guestUserId: 'test', roomId: 'room', ...query,
      totalAmount: 240, nightlyPriceSnapshot: 120, currency: 'USD', status: 'PENDING_PAYMENT', expiresAt: '2030-01-01T12:15:00Z' }, { status: 201, statusText: 'Created' }); fixture.detectChanges();
    for (const value of ['reservation-real', '240.00 USD', 'PENDING_PAYMENT', '12:15:00', 'No está confirmada']) expect(page.textContent).toContain(value);
    expect(page.querySelector('.create-booking')).toBeNull();
  });
  it.each(['HOTEL_ADMIN', 'SUPER_ADMIN'])('does not offer booking to %s', role => {
    const { page, http } = setup(role); expect(page.querySelector('.create-booking')).toBeNull(); http.expectNone(api);
  });
  it('sends anonymous visitors to login with safe criteria and without identity', () => {
    const { page, http } = setup(null); const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    page.querySelector<HTMLButtonElement>('.login-booking')!.click();
    const args = navigate.mock.calls[0]; expect(args[0]).toEqual(['/login']);
    const destination = args[1]!.queryParams!['returnUrl']; expect(destination).toContain('/hotels/hotel?');
    expect(destination).toContain('checkIn=2030-01-01'); expect(destination).not.toContain('roomId'); expect(destination).not.toContain('token'); http.expectNone(api);
  });
  it.each([400, 401, 403, 404, 409, 0, 500])('handles HTTP %s without automatic retry', status => {
    const { fixture, page, confirm, submit, http } = setup(); confirm(); submit(); const request = http.expectOne(api);
    if (status === 0) request.error(new ProgressEvent('error')); else request.flush({}, { status, statusText: 'Rejected' });
    fixture.detectChanges(); expect(page.querySelector('[role="alert"]')).not.toBeNull(); http.expectNone(api);
    if (status === 0 || status === 500) { expect(page.textContent).toContain('Resultado incierto'); expect(page.querySelector('.create-booking')).toBeNull(); }
    if (status === 401) expect(page.querySelector('.login-booking')).not.toBeNull();
    if (status === 409) expect(page.querySelector<HTMLButtonElement>('.create-booking')!.disabled).toBe(true);
  });
  it.each(['https://evil.test', '//evil.test', '/\\evil.test', '/hotels/id/../../login', '/hotels/id#token', '/admin/hotel', '/hotels/id%2fother'])('rejects unsafe return %s', value => {
    expect(bookingReturn(value)).toBeNull();
  });
  it('strips unknown/sensitive return parameters', () => {
    expect(bookingReturn('/hotels/id?page=2&token=secret&returnUrl=https://evil.test')).toBe('/hotels/id?page=2');
  });
  it('service excludes protected fields even if passed at runtime', () => {
    TestBed.inject(ReservationService).create({ roomId: 'room', ...query, guestUserId: 'other', totalAmount: 1 } as typeof query & { roomId: string }).subscribe();
    const request = TestBed.inject(HttpTestingController).expectOne(api); expect(request.request.body).toEqual({ roomId: 'room', ...query }); request.flush({});
  });
});

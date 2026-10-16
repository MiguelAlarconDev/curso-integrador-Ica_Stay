import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AUTH_STORAGE_KEY, AuthService } from '../../core/services/auth.service';
import { authInterceptor } from '../../core/interceptors/auth.interceptor';
import { UserReservationView } from '../../core/models/reservation';
import { PaymentService } from '../../core/services/payment.service';

describe('Reservation simulated payments', () => {
  const detailUrl = 'http://localhost:8080/api/v1/reservations/own';
  const paymentUrl = 'http://localhost:8080/api/v1/payments/simulate';
  const item: UserReservationView = {
    id: 'own', hotelId: 'hotel', hotelName: 'Hotel del Valle', roomId: 'room', roomNumber: '101',
    checkIn: '2030-01-10', checkOut: '2030-01-12', guests: 2, status: 'PENDING_PAYMENT',
    expiresAt: '2030-01-01T12:15:00Z', createdAt: '2030-01-01T12:00:00Z',
    nightlyPriceSnapshot: 125.5, totalAmount: 251, currency: 'PEN', evaluatedAt: '2030-01-01T12:00:00Z', holdExpired: false,
  };
  function session(role = 'USER') {
    const now = Math.floor(Date.now() / 1000);
    const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
    sessionStorage.setItem(AUTH_STORAGE_KEY, `${encode({ alg: 'HS256' })}.${encode({ sub: 'user@example.test', userId: 'user-a', role, iat: now, exp: now + 3600 })}.c2ln`);
  }
  const http = () => TestBed.inject(HttpTestingController);
  const page = (h: RouterTestingHarness) => h.routeNativeElement!;
  const button = (h: RouterTestingHarness, selector: string) => page(h).querySelector<HTMLButtonElement>(selector)!;
  function click(h: RouterTestingHarness, selector: string) { button(h, selector).click(); h.detectChanges(); }
  async function open(value: UserReservationView = item) {
    session(); const h = await RouterTestingHarness.create('/my-reservations/own');
    http().expectOne(detailUrl).flush(value); h.detectChanges(); await h.fixture.whenStable(); h.detectChanges();
    click(h, '.pay-toggle'); return h;
  }
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()] });
  });
  afterEach(() => { http().verify(); sessionStorage.clear(); vi.restoreAllMocks(); });

  it('sends only the contract with JWT and refreshes APPROVED from backend', async () => {
    const h = await open();
    for (const text of ['Hotel del Valle', '101', '251.00 PEN', '12:15:00', 'Simular pago aprobado', 'Simular pago rechazado']) {
      expect(page(h).textContent).toContain(text);
    }
    expect(page(h).querySelector('input')).toBeNull();
    click(h, '.approve-payment');
    const request = http().expectOne(paymentUrl);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ reservationId: 'own', result: 'APPROVED' });
    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${sessionStorage.getItem(AUTH_STORAGE_KEY)}`);
    request.flush({ id: 'attempt', result: 'APPROVED', reference: 'sim-unique' }); h.detectChanges();
    expect(page(h).textContent).toContain('PENDING_PAYMENT');
    expect(button(h, '.verify-payment').disabled).toBe(true);
    http().expectOne(detailUrl).flush({ ...item, status: 'CONFIRMED' }); h.detectChanges();
    expect(page(h).textContent).toContain('CONFIRMED');
    expect(button(h, '.pay-toggle').disabled).toBe(true);
  });

  it('keeps REJECTED pending with original expiry and allows an explicit subsequent attempt', async () => {
    const h = await open(); click(h, '.reject-payment');
    http().expectOne(paymentUrl).flush({ id: 'attempt', result: 'REJECTED', reference: 'sim-rejected' }); h.detectChanges();
    expect(page(h).textContent).toContain('Pago simulado rechazado');
    expect(page(h).textContent).toContain('PENDING_PAYMENT'); expect(page(h).textContent).toContain('12:15:00');
    expect(page(h).textContent).toContain('251.00 PEN');
    http().expectNone(detailUrl);
    click(h, '.approve-payment');
    http().expectOne(paymentUrl).flush({ id: 'second', result: 'APPROVED', reference: 'sim-second' });
    http().expectOne(detailUrl).flush({ ...item, status: 'CONFIRMED' });
  });

  it('blocks double clicks and competing subscriptions while a payment is in flight', async () => {
    const h = await open(); const approve = button(h, '.approve-payment');
    approve.click(); approve.click(); button(h, '.reject-payment').click(); h.detectChanges();
    expect(button(h, '.approve-payment').disabled).toBe(true);
    let rejected = false;
    TestBed.inject(PaymentService).simulate({ reservationId: 'own', result: 'APPROVED' }).subscribe({ error: () => rejected = true });
    expect(rejected).toBe(true);
    http().expectOne(paymentUrl).flush({ id: 'attempt', result: 'APPROVED', reference: 'sim-unique' });
    http().expectOne(detailUrl).flush({ ...item, status: 'CONFIRMED' });
    http().expectNone(paymentUrl);
  });

  it.each(['CONFIRMED', 'CANCELLED', 'EXPIRED'] as const)('disables payment for %s', async status => {
    const h = await open({ ...item, status });
    expect(button(h, '.pay-toggle').disabled).toBe(true); http().expectNone(paymentUrl);
  });
  it.each([
    { holdExpired: true }, { expiresAt: item.evaluatedAt }, { expiresAt: null },
  ])('disables payment for expired or missing retention %j', async change => {
    const h = await open({ ...item, ...change }); expect(button(h, '.pay-toggle').disabled).toBe(true);
    http().expectNone(paymentUrl);
  });

  it('checks elapsed server retention again on click, even before the next timer tick', async () => {
    const clock = vi.spyOn(performance, 'now').mockReturnValue(0);
    const h = await open({ ...item, expiresAt: '2030-01-01T12:00:01Z' });
    expect(button(h, '.approve-payment').disabled).toBe(false);
    clock.mockReturnValue(1000); click(h, '.approve-payment'); http().expectNone(paymentUrl);
    await new Promise(resolve => setTimeout(resolve, 1100)); h.detectChanges();
    expect(button(h, '.approve-payment').disabled).toBe(true);
    expect(page(h).textContent).toContain('La retención ha expirado');
  });

  it.each([0, 409, 500, 503])('does not repeat uncertain/conflicting payment HTTP %s and offers verification', async status => {
    const h = await open(); click(h, '.approve-payment');
    const request = http().expectOne(paymentUrl);
    if (status === 0) request.error(new ProgressEvent('error'));
    else request.flush({ message: 'PRIVATE DETAIL' }, { status, statusText: 'Failure' });
    h.detectChanges();
    expect(page(h).querySelector('[role="alert"]')).not.toBeNull();
    expect(page(h).textContent).not.toContain('PRIVATE DETAIL');
    expect(button(h, '.approve-payment').disabled).toBe(true);
    click(h, '.verify-payment'); http().expectOne(detailUrl).flush(item); h.detectChanges();
    expect(page(h).textContent).toContain('Esto no demuestra que el pago haya fallado');
    expect(button(h, '.pay-toggle').disabled).toBe(true); http().expectNone(paymentUrl);
    click(h, '.verify-payment'); http().expectOne(detailUrl).flush({ ...item, status: 'CONFIRMED' }); h.detectChanges();
    expect(page(h).textContent).toContain('CONFIRMED');
  });

  it.each([400, 403, 404])('handles definitive HTTP %s without exposing server details', async status => {
    const h = await open(); click(h, '.reject-payment');
    http().expectOne(paymentUrl).flush({ message: 'PRIVATE DETAIL' }, { status, statusText: 'Failure' }); h.detectChanges();
    expect(page(h).querySelector('[role="alert"]')).not.toBeNull(); expect(page(h).textContent).not.toContain('PRIVATE DETAIL');
    expect(button(h, '.pay-toggle').disabled).toBe(true); http().expectNone(paymentUrl);
    click(h, '.verify-payment'); http().expectOne(detailUrl).flush({}, { status, statusText: 'Failure' }); h.detectChanges();
    expect(button(h, '.pay-toggle').disabled).toBe(true);
  });

  it('handles 401 using the existing interceptor and removes private detail', async () => {
    const h = await open(); click(h, '.approve-payment');
    http().expectOne(paymentUrl).flush({}, { status: 401, statusText: 'Unauthorized' });
    await h.fixture.whenStable(); h.detectChanges();
    expect(TestBed.inject(AuthService).session()).toBeNull(); expect(TestBed.inject(Router).url).toBe('/login');
    expect(page(h).textContent).not.toContain('Hotel del Valle');
  });

  it('keeps approval blocked if the subsequent GET fails and retries only the GET', async () => {
    const h = await open(); click(h, '.approve-payment');
    http().expectOne(paymentUrl).flush({ id: 'attempt', result: 'APPROVED', reference: 'sim-approved' });
    http().expectOne(detailUrl).flush({}, { status: 500, statusText: 'Failure' }); h.detectChanges();
    expect(page(h).textContent).toContain('No repitas el pago'); expect(button(h, '.pay-toggle').disabled).toBe(true);
    click(h, '.verify-payment'); http().expectOne(detailUrl).flush({ ...item, status: 'CONFIRMED' }); h.detectChanges();
    expect(page(h).textContent).toContain('CONFIRMED'); http().expectNone(paymentUrl);
  });

  it('does not interpret an invalid success body as a safe retry', async () => {
    const h = await open(); click(h, '.approve-payment'); http().expectOne(paymentUrl).flush({}); h.detectChanges();
    expect(button(h, '.pay-toggle').disabled).toBe(true); expect(page(h).querySelector('.verify-payment')).not.toBeNull();
    http().expectNone(paymentUrl);
  });

  it('preserves uncertainty when navigating away during a payment and returning', async () => {
    const h = await open(); click(h, '.approve-payment'); const pending = http().expectOne(paymentUrl);
    await h.navigateByUrl('/my-reservations');
    expect(pending.cancelled).toBe(true);
    http().expectOne(request => request.url.endsWith('/reservations/me')).flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    await h.navigateByUrl('/my-reservations/own'); http().expectOne(detailUrl).flush(item); h.detectChanges();
    expect(button(h, '.pay-toggle').disabled).toBe(true); expect(page(h).querySelector('.verify-payment')).not.toBeNull();
    http().expectNone(paymentUrl);
  });

  it.each(['HOTEL_ADMIN', 'SUPER_ADMIN'])('retains the USER guard for %s', async role => {
    session(role); await RouterTestingHarness.create('/my-reservations/own');
    expect(TestBed.inject(Router).url).toBe('/');
    http().match(request => request.url.endsWith('/hotels')).forEach(request => request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
    http().expectNone(paymentUrl); http().expectNone(detailUrl);
  });
  it('retains the authentication guard', async () => {
    await RouterTestingHarness.create('/my-reservations/own');
    expect(TestBed.inject(Router).url).toBe('/login'); http().expectNone(() => true);
  });
});

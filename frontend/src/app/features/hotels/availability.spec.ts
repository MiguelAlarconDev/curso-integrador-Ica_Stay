import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Availability } from './availability';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

describe('Availability', () => {
  const api = 'http://localhost:8080/api/v1/hotels/own/rooms';
  const room = { id: 'room', number: '101', description: null, capacity: 2, pricePerNight: 100, currency: 'PEN' };
  const date = (days: number) => new Date(Date.now() + days * 86400000).toISOString().slice(0, 10);
  beforeEach(() => { sessionStorage.clear(); TestBed.configureTestingModule({ imports: [Availability], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] }); });
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  function setup() {
    const fixture = TestBed.createComponent(Availability); fixture.componentRef.setInput('hotelId', 'own'); fixture.detectChanges();
    const page: HTMLElement = fixture.nativeElement;
    const http = TestBed.inject(HttpTestingController);
    const fill = (field: string, value: string) => {
      const input = page.querySelector<HTMLInputElement>(`[formControlName="${field}"]`)!;
      input.value = value; input.dispatchEvent(new Event('input')); fixture.detectChanges();
    };
    const submit = () => { page.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); fixture.detectChanges(); };
    const valid = () => { fill('checkIn', date(1)); fill('checkOut', date(3)); fill('guests', '2'); };
    return { fixture, page, http, fill, submit, valid };
  }
  it('invalidates selected room and its confirmation when criteria change', () => {
    const { valid, submit, http, fixture, page, fill } = setup(); valid(); submit();
    http.expectOne(r => r.url === api).flush([room]); fixture.detectChanges();
    page.querySelector<HTMLButtonElement>('.select-room')!.click(); fixture.detectChanges();
    expect(page.querySelector('app-booking')).not.toBeNull();
    expect(page.textContent).toContain('2 noches');
    fill('guests', '1'); expect(page.querySelector('app-booking')).toBeNull();
    expect(page.querySelector('.select-room')).toBeNull(); http.expectNone(() => true);
  });
  it('restores return criteria and rechecks without automatically selecting or posting', () => {
    TestBed.overrideProvider(ActivatedRoute, { useValue: { snapshot: { queryParamMap: convertToParamMap({ checkIn: date(1), checkOut: date(3), guests: '2' }) } } });
    const { fixture, page, http } = setup();
    const request = http.expectOne(r => r.url === api); expect(request.request.params.get('guests')).toBe('2');
    request.flush([room]); fixture.detectChanges();
    expect(page.querySelector('.select-room')).not.toBeNull(); expect(page.querySelector('app-booking')).toBeNull();
    http.expectNone(r => r.method === 'POST');
  });
  it('validates before sending and shows all returned fields', () => {
    const { valid, submit, http, fixture, page } = setup(); submit(); http.expectNone(() => true);
    expect(page.querySelector('[role="alert"]')).not.toBeNull(); valid(); submit(); submit();
    const request = http.expectOne(r => r.url === api);
    expect(request.request.params.keys().sort()).toEqual(['checkIn', 'checkOut', 'guests']);
    expect(request.request.params.get('checkIn')).toBe(date(1)); expect(request.request.params.get('guests')).toBe('2');
    expect(page.textContent).toContain('Consultando'); request.flush([room]); fixture.detectChanges();
    for (const text of ['101', 'Sin descripción', '2 huéspedes', '100.00 PEN']) expect(page.textContent).toContain(text);
  });
  it('rejects past dates, reversed range and fractional/nonpositive capacity', () => {
    const { valid, fill, submit, http } = setup();
    for (const [field, value] of [['checkIn', date(-1)], ['checkOut', date(0)], ['guests', '0'], ['guests', '1.5']]) {
      valid(); fill(field, value); submit(); http.expectNone(() => true);
    }
  });
  it('cancels obsolete queries on editing and destruction and shows empty results', () => {
    const { valid, fill, submit, http, fixture, page } = setup(); valid(); submit();
    const first = http.expectOne(r => r.url === api); fill('guests', '3'); expect(first.cancelled).toBe(true); submit();
    http.expectOne(r => r.url === api).flush([]); fixture.detectChanges(); expect(page.textContent).toContain('No hay habitaciones');
    fill('guests', '4'); submit(); const pending = http.expectOne(r => r.url === api); fixture.destroy(); expect(pending.cancelled).toBe(true);
  });
  it.each([0, 400, 404, 500])('handles HTTP %s and retries', status => {
    const { valid, submit, http, fixture, page } = setup(); valid(); submit(); const request = http.expectOne(r => r.url === api);
    if (status === 0) request.error(new ProgressEvent('error')); else request.flush({}, { status, statusText: 'Rejected' });
    fixture.detectChanges(); expect(page.querySelector('[role="alert"]')).not.toBeNull();
    page.querySelector<HTMLButtonElement>('.retry-rooms')!.click(); http.expectOne(r => r.url === api).flush([room]); fixture.detectChanges();
    expect(page.querySelector('[role="alert"]')).toBeNull();
  });
});

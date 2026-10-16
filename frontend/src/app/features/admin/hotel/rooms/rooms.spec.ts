import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HotelRooms } from './rooms';
import { authInterceptor } from '../../../../core/interceptors/auth.interceptor';
import { AUTH_STORAGE_KEY, AuthService } from '../../../../core/services/auth.service';

describe('Hotel rooms', () => {
  const api = 'http://localhost:8080/api/v1/admin/hotel/rooms';
  const room = { id: 'room-own', number: '101', description: null, capacity: 2, pricePerNight: 120.5, currency: 'PEN', status: 'ACTIVE' };
  beforeEach(() => {
    sessionStorage.clear();
    const now = Math.floor(Date.now() / 1000);
    // Unsigned fixture, only for frontend tests.
    const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
    sessionStorage.setItem(AUTH_STORAGE_KEY, `${encode({ alg: 'HS256' })}.${encode({ sub: 'admin@example.test', userId: 'admin-test', role: 'HOTEL_ADMIN', iat: now, exp: now + 3600 })}.c2ln`);
    TestBed.configureTestingModule({ imports: [HotelRooms], providers: [
      provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(),
    ] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });

  function setup(load = true) {
    const fixture = TestBed.createComponent(HotelRooms); fixture.detectChanges();
    const page: HTMLElement = fixture.nativeElement;
    const http = TestBed.inject(HttpTestingController);
    if (load) { http.expectOne(api).flush([room]); fixture.detectChanges(); }
    const details = () => { page.querySelector<HTMLButtonElement>('.rooms button')!.click(); fixture.detectChanges(); };
    return { fixture, page, http, details };
  }

  it('loads once with Bearer and displays the real list including inactive rooms', () => {
    const { fixture, page, http } = setup(false);
    expect(page.textContent).toContain('Cargando habitaciones');
    const request = http.expectOne(api);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${sessionStorage.getItem(AUTH_STORAGE_KEY)}`);
    request.flush([room, { ...room, id: 'second', number: '102', status: 'INACTIVE' }]); fixture.detectChanges();
    expect(page.querySelectorAll('.rooms li').length).toBe(2);
    expect(page.textContent).toContain('Habitación 101');
    expect(page.textContent).toContain('120.50 PEN');
    expect(page.textContent).toContain('2 personas');
    expect(page.textContent).toContain('Activa'); expect(page.textContent).toContain('Inactiva');
    fixture.detectChanges(); http.expectNone(() => true);
  });

  it('renders an empty state without detail actions', () => {
    const { fixture, page, http } = setup(false);
    http.expectOne(api).flush([]); fixture.detectChanges();
    expect(page.textContent).toContain('todavía no tiene habitaciones');
    expect(page.querySelector('.rooms button')).toBeNull();
  });

  it('fetches fresh detail with Bearer and renders all fields, including nullable description', () => {
    const { fixture, page, http, details } = setup(); details(); details();
    expect(page.textContent).toContain('Cargando detalle');
    const request = http.expectOne(`${api}/${room.id}`);
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.has('Authorization')).toBe(true);
    request.flush({ ...room, capacity: 3 }); fixture.detectChanges();
    const detail = page.querySelector('#room-detail')!;
    expect(detail.textContent).toContain('3 personas');
    expect(detail.textContent).toContain('Sin descripción');
    expect(detail.textContent).toContain(room.id);
    expect(page.querySelector('.rooms button')?.getAttribute('aria-expanded')).toBe('true');
  });

  it.each([0, 401, 403, 404, 500])('handles list HTTP %s with retry and no duplicate requests', status => {
    const { fixture, page, http } = setup(false);
    const request = http.expectOne(api);
    if (status === 0) request.error(new ProgressEvent('error'));
    else request.flush({ message: 'PRIVATE DETAIL' }, { status, statusText: 'Rejected' });
    fixture.detectChanges();
    expect(page.querySelector('[role="alert"]')).not.toBeNull();
    expect(page.textContent).not.toContain('PRIVATE DETAIL');
    if (status === 404) expect(page.textContent).toContain('No tienes un hotel asociado');
    expect(TestBed.inject(AuthService).session() === null).toBe(status === 401);
    const retry = page.querySelector<HTMLButtonElement>('section button')!;
    retry.click(); retry.click(); fixture.detectChanges();
    http.expectOne(api).flush([room]); fixture.detectChanges();
    expect(page.textContent).toContain('Habitación 101');
    expect(page.querySelector('[role="alert"]')).toBeNull();
  });

  it.each([0, 401, 403, 404, 500])('handles detail HTTP %s without exposing server errors and retries', status => {
    const { fixture, page, http, details } = setup(); details();
    const request = http.expectOne(`${api}/${room.id}`);
    if (status === 0) request.error(new ProgressEvent('error'));
    else request.flush({ message: 'PRIVATE DETAIL' }, { status, statusText: 'Rejected' });
    fixture.detectChanges();
    expect(page.querySelector('#room-detail [role="alert"]')).not.toBeNull();
    expect(page.textContent).not.toContain('PRIVATE DETAIL');
    if (status === 404) expect(page.textContent).toContain('no pertenece a tu hotel');
    expect(TestBed.inject(AuthService).session() === null).toBe(status === 401);
    page.querySelector<HTMLButtonElement>('#room-detail button')!.click(); fixture.detectChanges();
    http.expectOne(`${api}/${room.id}`).flush({ ...room, description: 'Vista al patio' }); fixture.detectChanges();
    expect(page.querySelector('#room-detail')!.textContent).toContain('Vista al patio');
  });

  it('cancels an older detail request when choosing another room and on destruction', () => {
    const { fixture, page, http, details } = setup(false);
    http.expectOne(api).flush([room, { ...room, id: 'second', number: '102' }]); fixture.detectChanges();
    details(); const first = http.expectOne(`${api}/${room.id}`);
    page.querySelectorAll<HTMLButtonElement>('.rooms button[aria-controls]')[1].click(); fixture.detectChanges();
    expect(first.cancelled).toBe(true);
    const second = http.expectOne(`${api}/second`);
    fixture.destroy(); expect(second.cancelled).toBe(true);
  });

  function editor() {
    const context = setup();
    const { page, fixture } = context;
    const fill = (name: string, value: string) => {
      const field = page.querySelector<HTMLInputElement>(`[formControlName="${name}"]`)!;
      field.value = value; field.dispatchEvent(new Event('input')); field.dispatchEvent(new Event('blur')); fixture.detectChanges();
    };
    const submit = () => { page.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); fixture.detectChanges(); };
    const open = (edit = false) => {
      page.querySelector<HTMLButtonElement>(edit ? '.edit' : 'button')!.click(); fixture.detectChanges();
    };
    return { ...context, fill, submit, open };
  }

  it('creates with only allowed fields, blocks duplicates and adds the response to the list', () => {
    const { page, fixture, http, fill, submit, open } = editor(); open();
    fill('number', '202'); fill('pricePerNight', '150.25'); submit(); submit();
    const request = http.expectOne(api);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ number: '202', description: null, capacity: 1, pricePerNight: 150.25, currency: 'PEN' });
    expect(request.request.headers.has('Authorization')).toBe(true);
    expect(page.querySelector('fieldset')!.disabled).toBe(true);
    expect(page.textContent).toContain('Guardando habitación');
    request.flush({ ...room, id: 'new-room', number: '202' }, { status: 201, statusText: 'Created' }); fixture.detectChanges();
    expect(page.querySelector('form')).toBeNull();
    expect(page.querySelectorAll('.rooms li').length).toBe(2);
    expect(page.textContent).toContain('Habitación guardada correctamente');
  });

  it.each([
    ['number', ''], ['number', ' 101'], ['number', '101 '], ['number', 'x'.repeat(41)],
    ['capacity', '0'], ['capacity', '1.5'], ['capacity', '2147483648'],
    ['pricePerNight', '0'], ['pricePerNight', '-1'], ['pricePerNight', '1.001'], ['pricePerNight', '10000000000'],
    ['currency', 'pen'], ['currency', ''],
  ])('rejects invalid %s = %s without HTTP', (field, value) => {
    const { page, http, open, fill, submit } = editor(); open(true); fill(field, value); submit();
    expect(page.querySelector(`[formControlName="${field}"]`)!.getAttribute('aria-invalid')).toBe('true');
    http.expectNone(() => true);
  });

  it('edits the selected room only and updates list and open detail from the server response', () => {
    const { page, fixture, http, details, open, fill, submit } = editor();
    details(); http.expectOne(`${api}/${room.id}`).flush(room); fixture.detectChanges();
    open(true);
    expect(page.querySelector<HTMLInputElement>('[formControlName="number"]')!.value).toBe('101');
    fill('number', '103'); fill('description', 'Actualizada'); fill('currency', 'USD'); submit(); submit();
    const request = http.expectOne(`${api}/${room.id}`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ number: '103', description: 'Actualizada', capacity: 2, pricePerNight: 120.5, currency: 'USD' });
    request.flush({ ...room, number: '103', description: 'Actualizada', currency: 'USD' }); fixture.detectChanges();
    expect(page.querySelectorAll('.rooms li').length).toBe(1);
    expect(page.querySelector('.rooms')!.textContent).toContain('103');
    expect(page.querySelector('#room-detail')!.textContent).toContain('Actualizada');
    expect(page.querySelector('#room-detail')!.textContent).toContain('USD');
  });

  it.each([false, true])('cancels the form without writes (editing=%s)', edit => {
    const { page, fixture, http, open, fill } = editor(); open(edit); fill('number', '999');
    page.querySelector<HTMLButtonElement>('form button[type="button"]')!.click(); fixture.detectChanges();
    expect(page.querySelector('form')).toBeNull(); expect(page.textContent).toContain('Habitación 101');
    http.expectNone(() => true);
  });

  for (const edit of [false, true]) {
    it.each([0, 400, 401, 403, 404, 409, 500])(`preserves form after ${edit ? 'PUT' : 'POST'} failure %s`, status => {
      const { page, fixture, http, open, fill, submit } = editor(); open(edit);
      fill('number', '202'); fill('pricePerNight', '200'); submit();
      const request = http.expectOne(edit ? `${api}/${room.id}` : api);
      if (status === 0) request.error(new ProgressEvent('error'));
      else request.flush({ message: 'PRIVATE DETAIL' }, { status, statusText: 'Rejected' });
      fixture.detectChanges();
      expect(page.querySelector('form [role="alert"]')).not.toBeNull();
      expect(page.textContent).not.toContain('PRIVATE DETAIL');
      expect(page.querySelector<HTMLInputElement>('[formControlName="number"]')!.value).toBe('202');
      expect(page.querySelector('fieldset')!.disabled).toBe(false);
      expect(page.querySelector('.rooms')!.textContent).toContain('Habitación 101');
      if (status === 409) expect(page.textContent).toContain('Ya existe una habitación');
      expect(TestBed.inject(AuthService).session() === null).toBe(status === 401);
    });
  }

  it.each(['ACTIVE', 'INACTIVE'])('confirms a transition from %s and updates list and detail', status => {
    const { page, fixture, http, details } = setup(false);
    http.expectOne(api).flush([{ ...room, status }]); fixture.detectChanges();
    details(); http.expectOne(`${api}/${room.id}`).flush({ ...room, status }); fixture.detectChanges();
    page.querySelector<HTMLButtonElement>('.status-action')!.click(); fixture.detectChanges();
    http.expectNone(() => true);
    const confirmation = page.querySelector<HTMLButtonElement>('section[aria-labelledby="status-title"] button')!;
    confirmation.click(); confirmation.click(); fixture.detectChanges();
    const request = http.expectOne(`${api}/${room.id}/status`);
    const next = status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    expect(request.request.method).toBe('PATCH'); expect(request.request.body).toEqual({ status: next });
    request.flush({ ...room, status: next }); fixture.detectChanges();
    expect(page.querySelector('.rooms .status')!.textContent).toBe(next === 'ACTIVE' ? 'Activa' : 'Inactiva');
    expect(page.querySelector('#room-detail')!.textContent).toContain(next === 'ACTIVE' ? 'Activa' : 'Inactiva');
    expect(page.querySelector('#status-title')).toBeNull();
  });

  it('cancels status confirmation without a request', () => {
    const { page, fixture, http } = setup();
    page.querySelector<HTMLButtonElement>('.status-action')!.click(); fixture.detectChanges();
    page.querySelectorAll<HTMLButtonElement>('section[aria-labelledby="status-title"] button')[1].click(); fixture.detectChanges();
    http.expectNone(() => true); expect(page.querySelector('#status-title')).toBeNull();
    expect(page.querySelector('.status')!.textContent).toBe('Activa');
  });

  it.each([0, 400, 401, 403, 404, 409, 500])('preserves state and permits retry on PATCH error %s', status => {
    const { page, fixture, http } = setup();
    page.querySelector<HTMLButtonElement>('.status-action')!.click(); fixture.detectChanges();
    page.querySelector<HTMLButtonElement>('section[aria-labelledby="status-title"] button')!.click(); fixture.detectChanges();
    const request = http.expectOne(`${api}/${room.id}/status`);
    if (status === 0) request.error(new ProgressEvent('error'));
    else request.flush({}, { status, statusText: 'Rejected' });
    fixture.detectChanges();
    expect(page.querySelector('[role="alert"]')).not.toBeNull();
    expect(page.querySelector('.status')!.textContent).toBe('Activa');
    expect(page.querySelector<HTMLButtonElement>('section[aria-labelledby="status-title"] button')!.disabled).toBe(false);
  });
});

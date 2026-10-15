import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { HotelProfile } from './profile';
import { API_BASE_URL } from '../../../../core/api.config';
import { authInterceptor } from '../../../../core/interceptors/auth.interceptor';
import { AUTH_STORAGE_KEY, AuthService } from '../../../../core/services/auth.service';
import { AdminHotelService } from '../../../../core/services/admin-hotel.service';

describe('Hotel profile', () => {
  const hotel = { id: 'hotel-own', name: 'Hotel del Valle', description: null, address: 'Calle 1', city: 'Ica' };
  const api = 'http://localhost:8080/api/v1/admin/hotel';
  beforeEach(() => {
    sessionStorage.clear();
    const now = Math.floor(Date.now() / 1000);
    const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
    sessionStorage.setItem(AUTH_STORAGE_KEY, `${encode({ alg: 'HS256' })}.${encode({ sub: 'admin@example.test', userId: 'admin-test', role: 'HOTEL_ADMIN', iat: now, exp: now + 3600 })}.c2ln`);
    TestBed.configureTestingModule({ imports: [HotelProfile], providers: [provideRouter([]),
      { provide: API_BASE_URL, useValue: 'http://localhost:8080' },
      provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });

  function setup(load = true) {
    const fixture = TestBed.createComponent(HotelProfile); fixture.detectChanges();
    const page: HTMLElement = fixture.nativeElement;
    const http = TestBed.inject(HttpTestingController);
    if (load) { http.expectOne(api).flush(hotel); fixture.detectChanges(); }
    const field = (name: string) => page.querySelector<HTMLInputElement | HTMLTextAreaElement>(`[formControlName="${name}"]`)!;
    const fill = (name: string, value: string) => {
      field(name).value = value; field(name).dispatchEvent(new Event('input'));
      field(name).dispatchEvent(new Event('blur')); fixture.detectChanges();
    };
    const submit = () => { page.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); fixture.detectChanges(); };
    const save = () => page.querySelector<HTMLButtonElement>('button[type="submit"]')!;
    return { fixture, page, http, field, fill, submit, save };
  }

  it('loads once with existing Bearer, renders real data and starts pristine', () => {
    const { fixture, page, http, field, save } = setup(false);
    expect(page.textContent).toContain('Cargando');
    const request = http.expectOne(api);
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${sessionStorage.getItem(AUTH_STORAGE_KEY)}`);
    request.flush(hotel); fixture.detectChanges();
    expect(page.textContent).toContain(hotel.id);
    expect(field('name').value).toBe(hotel.name);
    expect(field('description').value).toBe('');
    expect(field('address').value).toBe(hotel.address);
    expect(field('city').value).toBe(hotel.city);
    expect(save().disabled).toBe(true);
    expect(page.querySelectorAll('input, textarea').length).toBe(4);
    expect(page.querySelector('form')!.classList.contains('ng-pristine')).toBe(true);
    fixture.detectChanges(); http.expectNone(api);
  });

  it.each([['name', 180], ['address', 255], ['city', 120], ['description', 5000]] as const)
    ('validates %s after stripping exterior whitespace', (name, max) => {
      const { fill, submit, save, http, field } = setup();
      fill(name, 'x'.repeat(max + 1));
      expect(save().disabled).toBe(true);
      expect(field(name).getAttribute('aria-invalid')).toBe('true');
      submit(); http.expectNone(api);
      fill(name, '  ' + 'x'.repeat(max) + '  ');
      expect(save().disabled).toBe(false);
      if (name !== 'description') {
        fill(name, '\u2003 \t'); expect(save().disabled).toBe(true);
        submit(); http.expectNone(api);
      }
    });

  it('does not save normalization-only changes', () => {
    const { fill, save, submit, http } = setup();
    fill('name', '  ' + hotel.name + '  '); fill('description', '   ');
    expect(save().disabled).toBe(true); submit(); http.expectNone(api);
  });

  it('sends only editable fields once, applies returned resource and resets dirty', () => {
    const { fill, submit, http, page, fixture, field, save } = setup();
    fill('name', '  Nombre nuevo  '); fill('description', '  ');
    fill('address', '  Calle nueva  '); fill('city', ' Paracas ');
    submit(); submit();
    const request = http.expectOne(api);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'Nombre nuevo', description: null, address: 'Calle nueva', city: 'Paracas' });
    expect(request.request.headers.has('Authorization')).toBe(true);
    expect(save().disabled).toBe(true);
    expect(page.querySelector('fieldset')!.disabled).toBe(true);
    expect(page.textContent).toContain('Guardando');
    const updated = { ...hotel, name: 'Nombre canónico', address: 'Calle nueva', city: 'Paracas' };
    request.flush(updated); fixture.detectChanges();
    expect(page.textContent).toContain('se guardaron correctamente');
    expect(field('name').value).toBe(updated.name);
    expect(page.querySelector('form')!.classList.contains('ng-pristine')).toBe(true);
    expect(save().disabled).toBe(true); http.expectNone(api);
  });

  it('discards edits and restores the latest saved values', () => {
    const { fill, submit, http, fixture, page, field, save } = setup();
    fill('name', 'Guardado'); submit(); http.expectOne(api).flush({ ...hotel, name: 'Guardado' }); fixture.detectChanges();
    fill('name', 'Descartado'); fill('description', 'Nueva descripción');
    page.querySelector<HTMLButtonElement>('.actions button[type="button"]')!.click(); fixture.detectChanges();
    expect(field('name').value).toBe('Guardado'); expect(field('description').value).toBe('');
    expect(save().disabled).toBe(true); http.expectNone(api);
  });

  it.each([0, 400, 403, 404, 409, 500])('preserves edits and session after failed PUT %s', status => {
    const { fill, submit, http, fixture, page, field, save } = setup();
    fill('name', 'Mis cambios'); submit();
    const request = http.expectOne(api);
    if (status === 0) request.error(new ProgressEvent('error'));
    else request.flush({ message: 'PRIVATE DETAIL' }, { status, statusText: 'Rejected' });
    fixture.detectChanges();
    expect(page.querySelector('[role="alert"]')).not.toBeNull();
    expect(page.textContent).not.toContain('PRIVATE DETAIL');
    expect(page.textContent).not.toContain('se guardaron correctamente');
    expect(field('name').value).toBe('Mis cambios');
    expect(page.querySelector('form')!.classList.contains('ng-dirty')).toBe(true);
    expect(save().disabled).toBe(false);
    expect(TestBed.inject(AuthService).session()).not.toBeNull();
  });

  it.each([0, 400, 403, 404, 500])('handles GET failure %s and retries without duplicate requests', status => {
    const { http, page, fixture } = setup(false);
    const request = http.expectOne(api);
    if (status === 0) request.error(new ProgressEvent('error'));
    else request.flush({}, { status, statusText: 'Rejected' });
    fixture.detectChanges();
    expect(page.querySelector('form')).toBeNull();
    expect(page.querySelector('[role="alert"]')).not.toBeNull();
    if (status === 404) expect(page.textContent).toContain('No tienes un hotel asociado');
    const retry = page.querySelector<HTMLButtonElement>('button')!;
    retry.click(); retry.click(); fixture.detectChanges();
    http.expectOne(api).flush(hotel); fixture.detectChanges();
    expect(page.querySelector('form')).not.toBeNull();
    expect(TestBed.inject(AuthService).session()).not.toBeNull();
  });

  it.each(['GET', 'PUT'])('lets the existing interceptor clear an invalid session on %s 401', method => {
    const { http, fill, submit, fixture, page } = setup(method === 'PUT');
    if (method === 'PUT') { fill('name', 'Nuevo'); submit(); }
    http.expectOne(api).flush({}, { status: 401, statusText: 'Unauthorized' }); fixture.detectChanges();
    expect(TestBed.inject(AuthService).session()).toBeNull();
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull();
    expect(page.textContent).toContain('Tu sesión ya no es válida');
  });

  it('service excludes protected properties even if runtime input contains them', () => {
    const service = TestBed.inject(AdminHotelService);
    service.update({ ...hotel, status: 'INACTIVE', adminUserId: 'another-user' } as typeof hotel).subscribe();
    const request = TestBed.inject(HttpTestingController).expectOne(api);
    expect(Object.keys(request.request.body).sort()).toEqual(['address', 'city', 'description', 'name']);
    request.flush(hotel);
  });
});

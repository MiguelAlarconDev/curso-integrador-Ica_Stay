import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AUTH_STORAGE_KEY, AuthService } from '../../core/services/auth.service';
import { authInterceptor } from '../../core/interceptors/auth.interceptor';

function token(role: string) {
  const now = Math.floor(Date.now() / 1000);
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS256' })}.${encode({ sub: 'super@example.test', userId: 'test-super', role, iat: now, exp: now + 3600 })}.c2ln`;
}
const values = { name: ' Hotel Nuevo ', description: ' Descripción ', address: ' Calle 123 ', city: ' Ica ',
  adminName: ' Admin Nuevo ', email: ' ADMIN@EXAMPLE.TEST ', password: 'HotelInitial123!', confirmation: 'HotelInitial123!' };
const row = { id: 'hotel-test', name: 'Hotel Nuevo', address: 'Calle 123', city: 'Ica', status: 'ACTIVE',
  adminUserId: 'admin-test', adminName: 'Admin Nuevo', adminEmail: 'admin@example.test' };

describe('Create hotel and administrator', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes),
      provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });
  const request = () => TestBed.inject(HttpTestingController).expectOne(r => r.method === 'POST' && r.url.endsWith('/api/v1/superadmin/hotels'));
  async function open() {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token('SUPER_ADMIN'));
    return RouterTestingHarness.create('/superadmin/hotels/new');
  }
  function fill(h: RouterTestingHarness, changes: Partial<typeof values> = {}) {
    for (const [key, value] of Object.entries({ ...values, ...changes })) {
      const input = h.routeNativeElement!.querySelector<HTMLInputElement | HTMLTextAreaElement>(`#${key}`)!;
      input.value = value; input.dispatchEvent(new Event('input'));
    }
    h.detectChanges();
  }
  function submit(h: RouterTestingHarness) {
    h.routeNativeElement!.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); h.detectChanges();
  }

  it('validates required fields without posting', async () => {
    const h = await open(); submit(h);
    expect(h.routeNativeElement!.querySelectorAll('[aria-invalid="true"]').length).toBe(7);
    expect(h.routeNativeElement!.textContent).toContain('Revisa este campo');
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it.each([
    { name: ' ' }, { name: 'x'.repeat(181) }, { address: 'x'.repeat(256) }, { city: 'x'.repeat(121) },
    { description: 'x'.repeat(5001) }, { adminName: 'x'.repeat(161) }, { email: 'bad' },
    { password: 'short', confirmation: 'short' }, { password: 'é'.repeat(37), confirmation: 'é'.repeat(37) },
    { confirmation: 'different' },
  ])('rejects invalid form values (%j)', async changes => {
    const h = await open(); fill(h, changes); submit(h);
    TestBed.inject(HttpTestingController).expectNone(() => true);
    expect(h.routeNativeElement!.querySelector('[aria-invalid="true"]')).not.toBeNull();
  });

  it('posts only allowed fields once, retains SUPER_ADMIN session and returns with confirmation', async () => {
    const h = await open(); const session = TestBed.inject(AuthService).token(); fill(h); submit(h); submit(h);
    const pending = request();
    expect(pending.request.headers.get('Authorization')).toBe(`Bearer ${session}`);
    expect(pending.request.body).toEqual({ name: 'Hotel Nuevo', description: 'Descripción', address: 'Calle 123', city: 'Ica',
      admin: { name: 'Admin Nuevo', email: 'admin@example.test', password: values.password } });
    expect(h.routeNativeElement!.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(true);
    expect(h.routeNativeElement!.querySelector<HTMLFieldSetElement>('fieldset')!.disabled).toBe(true);
    expect(h.routeNativeElement!.textContent).toContain('Creación en curso');
    pending.flush(row, { status: 201, statusText: 'Created' }); await h.fixture.whenStable(); h.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/superadmin/hotels?created=1');
    TestBed.inject(HttpTestingController).expectOne(r => r.method === 'GET' && r.url.endsWith('/api/v1/superadmin/hotels'))
      .flush({ content: [row], page: 0, size: 20, totalElements: 1, totalPages: 1 }); h.detectChanges();
    expect(h.routeNativeElement!.textContent).toContain('Hotel y administrador creados correctamente');
    expect(TestBed.inject(AuthService).token()).toBe(session);
    expect(sessionStorage.length).toBe(1);
    expect(JSON.stringify(sessionStorage)).not.toContain(values.password);
  });

  it.each([
    [400, 'Revisa los datos'], [403, 'No tienes permiso'], [409, 'correo del administrador ya está registrado'],
    [0, 'Revisa el listado'], [500, 'No pudimos crear el hotel'],
  ])('shows specific safe error %s, clears secrets and enables another attempt', async (status, message) => {
    const h = await open(); fill(h); submit(h); const pending = request();
    if (status === 0) pending.error(new ProgressEvent('error'));
    else pending.flush({ detail: 'PRIVATE' }, { status: Number(status), statusText: 'Failure' });
    h.detectChanges();
    expect(h.routeNativeElement!.textContent).toContain(message);
    expect(h.routeNativeElement!.textContent).not.toContain('PRIVATE');
    expect(h.routeNativeElement!.querySelector<HTMLInputElement>('#password')!.value).toBe('');
    expect(h.routeNativeElement!.querySelector<HTMLInputElement>('#confirmation')!.value).toBe('');
    expect(h.routeNativeElement!.querySelector<HTMLInputElement>('#name')!.value).toBe('Hotel Nuevo');
    expect(h.routeNativeElement!.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(false);
    fill(h); submit(h); request().flush({}, { status: 409, statusText: 'Conflict' });
  });

  it('links from hotel listing to the form', async () => {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token('SUPER_ADMIN'));
    const h = await RouterTestingHarness.create('/superadmin/hotels');
    TestBed.inject(HttpTestingController).expectOne(r => r.url.endsWith('/api/v1/superadmin/hotels'))
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }); h.detectChanges();
    h.routeNativeElement!.querySelector<HTMLAnchorElement>('a[href="/superadmin/hotels/new"]')!.click();
    await h.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/superadmin/hotels/new');
    expect(h.routeNativeElement!.querySelector('input[type="password"]')).not.toBeNull();
  });

  it.each(['USER', 'HOTEL_ADMIN', null])('guards the creation route against %s', async role => {
    if (role) sessionStorage.setItem(AUTH_STORAGE_KEY, token(role));
    await RouterTestingHarness.create('/superadmin/hotels/new');
    expect(TestBed.inject(Router).url).toBe(role ? '/' : '/login');
    TestBed.inject(HttpTestingController).match(r => r.url.endsWith('/api/v1/hotels'))
      .forEach(r => r.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
    TestBed.inject(HttpTestingController).expectNone(r => r.url.includes('/superadmin/'));
  });
});

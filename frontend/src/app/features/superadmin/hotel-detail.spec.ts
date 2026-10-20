import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AUTH_STORAGE_KEY } from '../../core/services/auth.service';
import { authInterceptor } from '../../core/interceptors/auth.interceptor';

const hotel = { id: 'hotel-test', name: 'Original', description: 'Descripción', address: 'Calle 1', city: 'Ica',
  status: 'ACTIVE', adminUserId: 'admin-test', adminName: 'Admin', adminEmail: 'admin@example.test' };
function token(role: string) {
  const now = Math.floor(Date.now() / 1000);
  const encode = (v: unknown) => btoa(JSON.stringify(v)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS256' })}.${encode({ sub: 'super@example.test', userId: 'test-super', role, iat: now, exp: now + 3600 })}.c2ln`;
}
describe('SUPER_ADMIN hotel detail and edits', () => {
  beforeEach(() => {
    sessionStorage.clear(); TestBed.configureTestingModule({ providers: [provideRouter(routes),
      provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });
  const request = (method = 'GET') => TestBed.inject(HttpTestingController).expectOne(r => r.method === method
    && r.url.endsWith('/api/v1/superadmin/hotels/hotel-test' + (method === 'PATCH' ? '/status' : '')));
  async function open(flush = true) {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token('SUPER_ADMIN'));
    const h = await RouterTestingHarness.create('/superadmin/hotels/hotel-test?name=Original&page=2');
    if (flush) { request().flush(hotel); h.detectChanges(); } return h;
  }
  function fill(h: RouterTestingHarness, key: string, value: string) {
    const el = h.routeNativeElement!.querySelector<HTMLInputElement>(`#${key}`)!;
    el.value = value; el.dispatchEvent(new Event('input')); h.detectChanges();
  }
  function submit(h: RouterTestingHarness) {
    h.routeNativeElement!.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); h.detectChanges();
  }
  function click(h: RouterTestingHarness, selector: string) { h.routeNativeElement!.querySelector<HTMLElement>(selector)!.click(); h.detectChanges(); }

  it('loads a safe detail with JWT and restores editable fields', async () => {
    const h = await open(false); expect(h.routeNativeElement!.textContent).toContain('Cargando hotel');
    const pending = request(); expect(pending.request.headers.has('Authorization')).toBe(true);
    pending.flush(hotel); h.detectChanges();
    expect(h.routeNativeElement!.textContent).toContain('admin@example.test');
    expect(h.routeNativeElement!.querySelector<HTMLInputElement>('#name')!.value).toBe('Original');
    expect(h.routeNativeElement!.querySelector<HTMLTextAreaElement>('#description')!.value).toBe('Descripción');
    expect(h.routeNativeElement!.querySelector('[formControlName="adminUserId"]')).toBeNull();
  });
  it.each(['ACTIVE', 'INACTIVE'])('opens the listed %s hotel by its hotel UUID using the administrative endpoint', async status => {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token('SUPER_ADMIN'));
    const id = '22222222-2222-4222-8222-222222222222';
    const adminUserId = '11111111-1111-4111-8111-111111111111';
    const listed = { ...hotel, id, adminUserId, status };
    const h = await RouterTestingHarness.create('/superadmin/hotels?status=' + status);
    TestBed.inject(HttpTestingController).expectOne(r => r.url.endsWith('/api/v1/superadmin/hotels'))
      .flush({ content: [listed], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    h.detectChanges();
    const link = h.routeNativeElement!.querySelector<HTMLAnchorElement>(`a[href^="/superadmin/hotels/${id}"]`)!;
    expect(link).not.toBeNull(); link.click(); await h.fixture.whenStable();
    const detail = TestBed.inject(HttpTestingController).expectOne(r =>
      r.method === 'GET' && r.url === `http://localhost:8080/api/v1/superadmin/hotels/${id}`);
    expect(detail.request.headers.has('Authorization')).toBe(true);
    TestBed.inject(HttpTestingController).expectNone(r => r.url.includes('/api/v1/hotels/') || r.url.endsWith(adminUserId));
    detail.flush(listed); h.detectChanges();
    expect(h.routeNativeElement!.querySelector<HTMLInputElement>('#name')!.value).toBe(hotel.name);
    expect(h.routeNativeElement!.querySelector('button[type="submit"]')).not.toBeNull();
    expect(h.routeNativeElement!.querySelector('.change-status')!.textContent)
      .toContain(status === 'ACTIVE' ? 'Desactivar hotel' : 'Activar hotel');
    expect(h.routeNativeElement!.textContent).not.toContain('El hotel no existe');
  });
  it('validates fields without sending', async () => {
    const h = await open(); fill(h, 'name', ' '); fill(h, 'city', 'x'.repeat(121)); submit(h);
    TestBed.inject(HttpTestingController).expectNone(r => r.method === 'PUT');
    expect(h.routeNativeElement!.textContent).toContain('Revisa este campo');
  });
  it('saves only allowed fields once and refreshes detail and list', async () => {
    const h = await open(); fill(h, 'name', ' Nuevo '); submit(h); submit(h);
    const pending = request('PUT'); expect(pending.request.body).toEqual({ name: 'Nuevo', description: 'Descripción', address: 'Calle 1', city: 'Ica' });
    expect(h.routeNativeElement!.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(true);
    pending.flush({ ...hotel, name: 'Nuevo' }); h.detectChanges();
    expect(h.routeNativeElement!.textContent).toContain('Datos del hotel guardados');
    expect(h.routeNativeElement!.querySelector('h2')!.textContent).toBe('Nuevo');
    click(h, '.back'); await h.fixture.whenStable();
    const list = TestBed.inject(HttpTestingController).expectOne(r => r.url.endsWith('/api/v1/superadmin/hotels'));
    expect(list.request.params.get('page')).toBe('2'); expect(list.request.params.get('name')).toBe('Original');
    list.flush({ content: [], page: 2, size: 20, totalElements: 0, totalPages: 0 });
  });
  it('requires explicit confirmation for both state transitions and cancellation does not send', async () => {
    const h = await open(); click(h, '.change-status');
    expect(h.routeNativeElement!.textContent).toContain('¿Confirmas desactivar Original?');
    TestBed.inject(HttpTestingController).expectNone(r => r.method === 'PATCH');
    click(h, '.cancel-status'); TestBed.inject(HttpTestingController).expectNone(r => r.method === 'PATCH');
    click(h, '.change-status'); click(h, '.confirm-status'); click(h, '.confirm-status');
    const pending = request('PATCH'); expect(pending.request.body).toEqual({ status: 'INACTIVE' });
    pending.flush({ ...hotel, status: 'INACTIVE' }); h.detectChanges();
    expect(h.routeNativeElement!.textContent).toContain('Hotel desactivado');
    click(h, '.change-status'); expect(h.routeNativeElement!.textContent).toContain('¿Confirmas activar Original?');
    click(h, '.confirm-status'); const next = request('PATCH'); expect(next.request.body).toEqual({ status: 'ACTIVE' });
    next.flush(hotel); h.detectChanges(); expect(h.routeNativeElement!.textContent).toContain('Hotel activado');
  });
  it.each([400, 403, 500])('preserves edits on save error %s and allows retry', async status => {
    const h = await open(); fill(h, 'name', 'Edited'); submit(h);
    request('PUT').flush({ detail: 'PRIVATE' }, { status, statusText: 'Failure' }); h.detectChanges();
    expect(h.routeNativeElement!.querySelector('[role="alert"]')).not.toBeNull();
    expect(h.routeNativeElement!.textContent).not.toContain('PRIVATE');
    expect(h.routeNativeElement!.querySelector<HTMLInputElement>('#name')!.value).toBe('Edited');
    submit(h); request('PUT').flush({ ...hotel, name: 'Edited' });
  });
  it('keeps existing status on PATCH failure and asks for confirmation again', async () => {
    const h = await open(); click(h, '.change-status'); click(h, '.confirm-status');
    request('PATCH').flush({}, { status: 500, statusText: 'Failure' }); h.detectChanges();
    expect(h.routeNativeElement!.querySelector('strong')!.textContent).toBe('Activo');
    expect(h.routeNativeElement!.querySelector('.confirm-status')).toBeNull();
    click(h, '.change-status'); TestBed.inject(HttpTestingController).expectNone(r => r.method === 'PATCH');
  });
  it('handles load error and retry, then shows missing hotel without a form', async () => {
    const h = await open(false); request().error(new ProgressEvent('error')); h.detectChanges();
    click(h, '.retry'); request().flush({}, { status: 404, statusText: 'Not found' }); h.detectChanges();
    expect(h.routeNativeElement!.textContent).toContain('El hotel no existe');
    expect(h.routeNativeElement!.querySelector('form')).toBeNull();
    expect(h.routeNativeElement!.querySelector('.change-status')).toBeNull();
  });
  it('loads the new ID when navigation reuses the component', async () => {
    const h = await open(); await h.navigateByUrl('/superadmin/hotels/other');
    TestBed.inject(HttpTestingController).expectOne(r => r.url.endsWith('/superadmin/hotels/other')).flush({ ...hotel, id: 'other', name: 'Other' });
    h.detectChanges(); expect(h.routeNativeElement!.querySelector<HTMLInputElement>('#name')!.value).toBe('Other');
  });
  it.each(['USER', 'HOTEL_ADMIN', null])('guards against %s', async role => {
    if (role) sessionStorage.setItem(AUTH_STORAGE_KEY, token(role));
    await RouterTestingHarness.create('/superadmin/hotels/hotel-test');
    expect(TestBed.inject(Router).url).toBe(role ? '/' : '/login');
    TestBed.inject(HttpTestingController).match(r => r.url.endsWith('/api/v1/hotels'))
      .forEach(r => r.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
    TestBed.inject(HttpTestingController).expectNone(r => r.url.includes('/superadmin/'));
  });
});

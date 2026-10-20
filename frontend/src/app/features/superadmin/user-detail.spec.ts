import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AUTH_STORAGE_KEY, AuthService } from '../../core/services/auth.service';
import { authInterceptor } from '../../core/interceptors/auth.interceptor';
import { SuperadminUserDetail } from '../../core/models/superadmin-user';

function token(role: string): string {
  const now = Math.floor(Date.now() / 1000);
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS256' })}.${encode({ sub: 'admin@example.test', userId: 'test-admin', role, iat: now, exp: now + 3600 })}.c2ln`;
}
const data: SuperadminUserDetail = { id: '11111111-1111-4111-8111-111111111111', name: 'Test User', email: 'user@example.test', role: 'HOTEL_ADMIN', status: 'INACTIVE', hotel: { id: '22222222-2222-4222-8222-222222222222', name: 'Hotel Test', status: 'INACTIVE' } };
describe('Administrative user detail', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });
  const request = () => TestBed.inject(HttpTestingController).expectOne(r => r.url.endsWith('/api/v1/superadmin/users/' + data.id));
  async function open() {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token('SUPER_ADMIN'));
    return RouterTestingHarness.create('/superadmin/users/' + data.id);
  }
  it('opens from a list row with the user ID and preserves filters on return', async () => {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token('SUPER_ADMIN'));
    const h = await RouterTestingHarness.create('/superadmin/users?q=Test&role=HOTEL_ADMIN&page=1');
    TestBed.inject(HttpTestingController).expectOne(r => r.url.endsWith('/api/v1/superadmin/users')).flush({content:[data],page:1,size:20,totalElements:21,totalPages:2}); h.detectChanges();
    h.routeNativeElement!.querySelector<HTMLAnchorElement>('tbody a')!.click(); await h.fixture.whenStable();
    const pending=request(); expect(pending.request.headers.get('Authorization')).toBe(`Bearer ${TestBed.inject(AuthService).token()}`);
    pending.flush(data); h.detectChanges();
    expect(h.routeNativeElement!.textContent).toContain(data.email);
    expect(h.routeNativeElement!.querySelector('form')).toBeNull();
    expect(h.routeNativeElement!.querySelector<HTMLAnchorElement>('.hotel')!.getAttribute('href')).toBe('/superadmin/hotels/' + data.hotel!.id);
    h.routeNativeElement!.querySelector<HTMLAnchorElement>('.back')!.click(); await h.fixture.whenStable();
    const list=TestBed.inject(HttpTestingController).expectOne(r => r.url.endsWith('/api/v1/superadmin/users'));
    expect(list.request.params.get('q')).toBe('Test'); expect(list.request.params.get('page')).toBe('1'); list.flush({content:[],page:1,size:20,totalElements:0,totalPages:0});
  });
  it.each(['USER','HOTEL_ADMIN','SUPER_ADMIN'] as const)('shows %s without a hotel', async role => {
    const h=await open(); expect(h.routeNativeElement!.textContent).toContain('Cargando usuario');
    request().flush({...data,role,hotel:null});h.detectChanges();
    for (const text of [data.id,data.name,data.email,role,'Inactivo']) expect(h.routeNativeElement!.textContent).toContain(text);
    expect(h.routeNativeElement!.querySelector('.hotel')).toBeNull();
    if(role==='HOTEL_ADMIN') expect(h.routeNativeElement!.textContent).toContain('Sin hotel asignado');
  });
  it.each([400,403,404,500])('handles %s and retries',async status => {
    const h=await open();request().flush({detail:'PRIVATE'},{status,statusText:'Failure'});h.detectChanges();
    expect(h.routeNativeElement!.querySelector('[role="alert"]')).not.toBeNull();expect(h.routeNativeElement!.textContent).not.toContain('PRIVATE');
    if(status===404) expect(h.routeNativeElement!.textContent).toContain('El usuario no existe');
    h.routeNativeElement!.querySelector<HTMLButtonElement>('.retry')!.click();request().flush(data);h.detectChanges();
    expect(h.routeNativeElement!.querySelector('[role="alert"]')).toBeNull();expect(h.routeNativeElement!.textContent).toContain('Hotel Test');
  });
  it('cancels the request on navigation', async () => { const h=await open();const pending=request();await h.navigateByUrl('/login');expect(pending.cancelled).toBe(true); });
  it.each(['USER','HOTEL_ADMIN'])('denies %s',async role => {
    sessionStorage.setItem(AUTH_STORAGE_KEY,token(role));await RouterTestingHarness.create('/superadmin/users/'+data.id);
    expect(TestBed.inject(Router).url).toBe('/');
    TestBed.inject(HttpTestingController).match(r=>r.url.endsWith('/api/v1/hotels')).forEach(r=>r.flush({content:[],page:0,size:20,totalElements:0,totalPages:0}));
    TestBed.inject(HttpTestingController).expectNone(r=>r.url.includes('/superadmin/'));
  });
  it('redirects anonymous users',async()=>{await RouterTestingHarness.create('/superadmin/users/'+data.id);expect(TestBed.inject(Router).url).toBe('/login');});
  function click(h: RouterTestingHarness, selector: string) {
    h.routeNativeElement!.querySelector<HTMLButtonElement>(selector)!.click(); h.detectChanges();
  }
  const statusRequest = () => TestBed.inject(HttpTestingController).expectOne(r => r.url.endsWith('/api/v1/superadmin/users/' + data.id + '/status'));
  it.each(['ACTIVE', 'INACTIVE'] as const)('confirms changing %s, blocks duplicate submissions and refreshes detail', async initial => {
    const h = await open(); request().flush({ ...data, status: initial }); h.detectChanges();
    click(h, '.status-action');
    expect(h.routeNativeElement!.textContent).toContain('Confirma');
    TestBed.inject(HttpTestingController).expectNone(r => r.method === 'PATCH');
    click(h, '.cancel-status');
    expect(h.routeNativeElement!.querySelector('.confirm-status')).toBeNull();
    click(h, '.status-action'); click(h, '.confirm-status'); click(h, '.confirm-status');
    const pending = statusRequest(); const status = initial === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    expect(pending.request.method).toBe('PATCH'); expect(pending.request.body).toEqual({ status });
    expect(pending.request.headers.has('Authorization')).toBe(true);
    expect(h.routeNativeElement!.querySelector<HTMLButtonElement>('.confirm-status')!.disabled).toBe(true);
    expect(h.routeNativeElement!.textContent).toContain('Guardando estado');
    pending.flush({ ...data, status }); h.detectChanges();
    expect(h.routeNativeElement!.textContent).toContain('Estado del usuario actualizado');
    expect(h.routeNativeElement!.querySelector('.confirm-status')).toBeNull();
    expect(h.routeNativeElement!.querySelector('.status-action')!.textContent).toContain(status === 'ACTIVE' ? 'Desactivar' : 'Activar');
    expect(h.routeNativeElement!.querySelector('.hotel')).not.toBeNull();
    h.routeNativeElement!.querySelector<HTMLAnchorElement>('.back')!.click(); await h.fixture.whenStable();
    const list = TestBed.inject(HttpTestingController).expectOne(r => r.url.endsWith('/api/v1/superadmin/users'));
    list.flush({ content: [{ ...data, status }], page: 0, size: 20, totalElements: 1, totalPages: 1 }); h.detectChanges();
    expect(h.routeNativeElement!.querySelector('tbody')!.textContent).toContain(status === 'ACTIVE' ? 'Activo' : 'Inactivo');
  });
  it.each([400, 403, 404, 500])('handles status error %s without changing detail and allows a new attempt', async status => {
    const h = await open(); request().flush(data); h.detectChanges();
    click(h, '.status-action'); click(h, '.confirm-status');
    statusRequest().flush({ detail: 'PRIVATE' }, { status, statusText: 'Failure' }); h.detectChanges();
    expect(h.routeNativeElement!.querySelector('[role="alert"]')).not.toBeNull();
    expect(h.routeNativeElement!.textContent).not.toContain('PRIVATE');
    expect(h.routeNativeElement!.querySelector<HTMLButtonElement>('.status-action')!.disabled).toBe(false);
    expect(h.routeNativeElement!.querySelector('.status-action')!.textContent).toContain('Activar');
    click(h, '.status-action'); click(h, '.confirm-status'); statusRequest().flush({ ...data, status: 'ACTIVE' }); h.detectChanges();
    expect(h.routeNativeElement!.querySelector('[role="alert"]')).toBeNull();
  });
  it('disables self deactivation', async () => {
    const h = await open(); request().flush({ ...data, id: 'test-admin', role: 'SUPER_ADMIN', status: 'ACTIVE', hotel: null }); h.detectChanges();
    expect(h.routeNativeElement!.querySelector<HTMLButtonElement>('.status-action')!.disabled).toBe(true);
    click(h, '.status-action');
    TestBed.inject(HttpTestingController).expectNone(r => r.method === 'PATCH');
    expect(h.routeNativeElement!.querySelector('.confirm-status')).toBeNull();
  });
  it('cancels pending status responses on navigation to another account', async () => {
    const h = await open(); request().flush(data); h.detectChanges();
    click(h, '.status-action'); click(h, '.confirm-status'); const pending = statusRequest();
    await h.navigateByUrl('/superadmin/users/other'); expect(pending.cancelled).toBe(true);
    TestBed.inject(HttpTestingController).expectOne(r => r.url.endsWith('/api/v1/superadmin/users/other')).flush({ ...data, id: 'other', name: 'Other User' });h.detectChanges();
    expect(h.routeNativeElement!.textContent).toContain('Other User');
    expect(h.routeNativeElement!.querySelector('.confirm-status')).toBeNull();
    expect(h.routeNativeElement!.querySelector<HTMLButtonElement>('.status-action')!.disabled).toBe(false);
  });

});

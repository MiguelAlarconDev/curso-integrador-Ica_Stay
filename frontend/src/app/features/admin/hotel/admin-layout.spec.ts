import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../../app.routes';
import { AUTH_STORAGE_KEY, AuthService } from '../../../core/services/auth.service';

// Unsigned fixture for frontend tests only; backend authorization is unchanged.
function token(role: string): string {
  const now = Math.floor(Date.now() / 1000);
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS256' })}.${encode({ sub: 'admin@example.test', userId: 'test-admin', role, iat: now, exp: now + 3600 })}.c2ln`;
}

describe('Hotel administration routes', () => {
  const paths = ['/admin/hotel', '/admin/hotel/profile', '/admin/hotel/rooms', '/admin/hotel/reservations'];
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });

  it('renders a single header, session, four honest metrics and no HTTP requests', async () => {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token('HOTEL_ADMIN'));
    const harness = await RouterTestingHarness.create(paths[0]);
    const page = harness.routeNativeElement!;
    expect(page.querySelectorAll('header').length).toBe(1);
    expect(page.querySelector('h1')?.textContent).toBe('Resumen');
    expect(page.textContent).toContain('admin@example.test');
    expect(page.textContent).toContain('HOTEL_ADMIN');
    expect(page.querySelectorAll('article').length).toBe(4);
    page.querySelectorAll('article').forEach(card => expect(card.textContent).toContain('Próximamente'));
    expect(page.querySelector('a[href="/"]')).not.toBeNull();
    expect(page.querySelector('main')?.getAttribute('tabindex')).toBe('-1');
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('navigates all child sections and updates title and active link', async () => {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token('HOTEL_ADMIN'));
    const harness = await RouterTestingHarness.create(paths[0]);
    for (const [index, path] of paths.entries()) {
      const link = harness.routeNativeElement!.querySelector<HTMLAnchorElement>(`nav a[href="${path}"]`)!;
      link.click();
      await harness.fixture.whenStable(); harness.detectChanges();
      if (path.endsWith('/profile')) {
        TestBed.inject(HttpTestingController).expectOne(request => request.url.endsWith('/api/v1/admin/hotel'))
          .flush({ id: 'hotel-test', name: 'Hotel de prueba', description: null, address: 'Calle 1', city: 'Ica' });
        harness.detectChanges();
      }
      expect(TestBed.inject(Router).url).toBe(path);
      const page = harness.routeNativeElement!;
      expect(page.querySelectorAll('nav a.active').length).toBe(1);
      expect(page.querySelector('nav a[aria-current="page"]')?.getAttribute('href')).toBe(path);
      expect(page.querySelector('h1')?.textContent).toBe(['Resumen', 'Mi hotel', 'Habitaciones', 'Reservas'][index]);
      if (path.endsWith('/rooms')) {
        TestBed.inject(HttpTestingController).expectOne(request => request.url.endsWith('/api/v1/admin/hotel/rooms')).flush([]);
        harness.detectChanges();
        expect(page.textContent).toContain('todavía no tiene habitaciones');
      }
      if (index > 2) expect(page.textContent).toContain(`incremento 4.${index + 1}`);
    }
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it.each(paths)('redirects anonymous access to %s to login', async path => {
    await RouterTestingHarness.create(path);
    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it.each(['USER', 'SUPER_ADMIN'])('blocks %s on all sections without removing its session', async role => {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token(role));
    const harness = await RouterTestingHarness.create();
    for (const path of paths) {
      await harness.navigateByUrl(path);
      expect(TestBed.inject(Router).url).toBe('/');
      TestBed.inject(HttpTestingController).match(request => request.url.endsWith('/api/v1/hotels'))
        .forEach(request => request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
      expect(harness.routeNativeElement!.querySelector('app-admin-layout')).toBeNull();
    }
    expect(TestBed.inject(AuthService).role()).toBe(role);
  });

  it('logs out through the existing service and redirects to login', async () => {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token('HOTEL_ADMIN'));
    const harness = await RouterTestingHarness.create('/admin/hotel/rooms');
    TestBed.inject(HttpTestingController).expectOne(request => request.url.endsWith('/api/v1/admin/hotel/rooms')).flush([]);
    harness.routeNativeElement!.querySelector<HTMLButtonElement>('.sidebar-actions button')!.click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(AuthService).session()).toBeNull();
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/login');
    await harness.navigateByUrl('/admin/hotel/reservations');
    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('leaves the layout when the shared session is cleared', async () => {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token('HOTEL_ADMIN'));
    const harness = await RouterTestingHarness.create('/admin/hotel');
    TestBed.inject(AuthService).logout();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('keeps public routes accessible and offers the panel link only to hotel admins', async () => {
    const harness = await RouterTestingHarness.create('/login');
    expect(harness.routeNativeElement!.querySelector('form')).not.toBeNull();
    await harness.navigateByUrl('/register');
    expect(harness.routeNativeElement!.querySelector('form')).not.toBeNull();
    await harness.navigateByUrl('/');
    TestBed.inject(HttpTestingController).expectOne(request => request.url.endsWith('/api/v1/hotels'))
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    expect(harness.routeNativeElement!.querySelector('a[href="/admin/hotel"]')).toBeNull();
  });

  it('provides a way back to the panel from the public catalog', async () => {
    sessionStorage.setItem(AUTH_STORAGE_KEY, token('HOTEL_ADMIN'));
    const harness = await RouterTestingHarness.create('/');
    TestBed.inject(HttpTestingController).expectOne(request => request.url.endsWith('/api/v1/hotels'))
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    expect(harness.routeNativeElement!.querySelector('a[href="/admin/hotel"]')?.textContent).toContain('Mi panel');
  });
});

import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { API_BASE_URL } from './core/api.config';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    })
      .compileComponents();
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  function requestHotels() {
    const request = TestBed.inject(HttpTestingController).expectOne(
      `${TestBed.inject(API_BASE_URL)}/api/v1/hotels?page=0&size=20`,
    );
    expect(request.request.method).toBe('GET');
    return request;
  }

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('loads the public Home at / with its main sections', async () => {
    const harness = await RouterTestingHarness.create('/');
    requestHotels().flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    const page = harness.routeNativeElement!;
    expect(page.querySelector('h1')?.textContent).toContain('Encuentra dónde quedarte en Ica.');
    expect(page.querySelector('header')).not.toBeNull();
    expect(page.querySelector('#alojamientos')).not.toBeNull();
    expect(page.querySelector('#explorar')).not.toBeNull();
    expect(page.querySelector('footer')).not.toBeNull();
    expect(page.textContent).not.toContain('Hello, frontend');
  });

  it('labels all search controls and keeps unavailable actions disabled', async () => {
    const harness = await RouterTestingHarness.create('/');
    requestHotels().flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    const page = harness.routeNativeElement!;
    const controls = page.querySelectorAll('form input, form select');
    expect(controls.length).toBe(4);
    controls.forEach((control) => {
      expect(page.querySelector(`label[for="${control.id}"]`)?.textContent?.trim()).toBeTruthy();
    });
    expect(page.querySelector<HTMLButtonElement>('form button')?.disabled).toBe(true);
    expect(page.querySelectorAll('header button:disabled').length).toBe(2);
  });
  it('loads real hotel data while retaining only three editorial images', async () => {
    const harness = await RouterTestingHarness.create('/');
    expect(harness.routeNativeElement!.textContent).toContain('Cargando alojamientos');
    const content = Array.from({ length: 4 }, (_, i) => ({
      id: `hotel-${i}`, name: `Hotel de prueba ${i}`, description: i === 0 ? null : 'Descripción del hotel',
      address: `Calle ${i}`, city: 'Ica',
    }));
    requestHotels().flush({ content, page: 0, size: 20, totalElements: 4, totalPages: 1 });
    harness.detectChanges();
    const page = harness.routeNativeElement!;
    expect(page.querySelectorAll('.stay').length).toBe(3);
    expect(page.querySelectorAll('.stay-main').length).toBe(1);
    expect(page.textContent).toContain('Hotel de prueba 0');
    expect(page.textContent).toContain('Calle 0');
    expect(page.textContent).not.toContain('Hotel de prueba 3');
    expect(page.textContent).not.toContain('Cargando alojamientos');
    expect(Array.from(page.querySelectorAll('.stay img')).map(img => img.getAttribute('src'))).toEqual([
      '/images/home/hotel-vinedos.webp', '/images/home/hotel-dunas.webp', '/images/home/hotel-ciudad.webp',
    ]);
  });

  it('shows an empty state after a successful empty response', async () => {
    const harness = await RouterTestingHarness.create('/');
    requestHotels().flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('Todavía no hay alojamientos');
    expect(harness.routeNativeElement!.querySelectorAll('.stay').length).toBe(0);
  });

  it('keeps the Home usable after a network failure', async () => {
    const harness = await RouterTestingHarness.create('/');
    requestHotels().error(new ProgressEvent('error'));
    harness.detectChanges();
    const page = harness.routeNativeElement!;
    expect(page.textContent).toContain('No pudimos cargar los alojamientos');
    expect(page.textContent).not.toContain('Cargando alojamientos');
    expect(page.querySelector('#explorar')).not.toBeNull();
    expect(page.querySelector('footer')).not.toBeNull();
  });
});

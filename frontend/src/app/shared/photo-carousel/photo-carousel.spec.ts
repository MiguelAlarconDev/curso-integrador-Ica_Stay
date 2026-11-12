import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PhotoCarousel } from './photo-carousel';
import { PhotoView } from '../../core/models/photo';

describe('PhotoCarousel', () => {
  const api = 'http://localhost:8080/api/v1/hotels/h';
  const photo = (id: string, position: number, altText = ''): PhotoView => ({ id, position, altText, isCover: position === 0,
    url: `/api/v1/hotels/h/photos/${id}/content`, mimeType: 'image/png', sizeBytes: 10, width: 800, height: 600, createdAt: '' });
  beforeEach(() => TestBed.configureTestingModule({ imports: [PhotoCarousel], providers: [provideHttpClient(), provideHttpClientTesting()] }));
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  function setup(inputs: Record<string, unknown> = {}) {
    const fixture = TestBed.createComponent(PhotoCarousel);
    fixture.componentRef.setInput('hotelId', 'h'); fixture.componentRef.setInput('label', 'Hotel Oasis');
    Object.entries(inputs).forEach(([key, value]) => fixture.componentRef.setInput(key, value));
    fixture.detectChanges();
    const page: HTMLElement = fixture.nativeElement;
    const http = TestBed.inject(HttpTestingController);
    const current = () => Array.from(page.querySelectorAll('.slide')).findIndex(slide => slide.getAttribute('aria-hidden') === 'false');
    return { fixture, page, http, current };
  }

  it('renders accessible slides with eager first image and lazy secondary images', () => {
    const { fixture, page, http } = setup();
    expect(page.querySelector('.placeholder')).not.toBeNull();
    http.expectOne(`${api}/photos`).flush([photo('a', 0, 'Vista de la piscina'), photo('b', 1), photo('c', 2)]); fixture.detectChanges();
    const images = Array.from(page.querySelectorAll('img'));
    expect(images.map(image => image.getAttribute('loading'))).toEqual(['eager', 'lazy', 'lazy']);
    expect(images[0].getAttribute('src')).toBe(`${api}/photos/a/content`);
    expect(images[0].alt).toBe('Vista de la piscina'); expect(images[1].alt).toBe('Hotel Oasis, fotografía 2 de 3');
    expect(page.querySelector('section')!.getAttribute('aria-label')).toBe('Fotografías de Hotel Oasis');
    expect(page.querySelectorAll('.dot').length).toBe(3);
    expect(page.querySelector('.dot')!.getAttribute('aria-current')).toBe('true');
    expect(page.querySelector('.counter')!.textContent).toContain('1 / 3');
  });

  it('navigates with arrows, indicators and keyboard, wrapping at the ends', () => {
    const { fixture, page, http, current } = setup();
    http.expectOne(`${api}/photos`).flush([photo('a', 0), photo('b', 1), photo('c', 2)]); fixture.detectChanges();
    const press = (key: string) => { page.querySelector('section')!.dispatchEvent(new KeyboardEvent('keydown', { key })); fixture.detectChanges(); };
    page.querySelector<HTMLButtonElement>('.carousel-next')!.click(); fixture.detectChanges(); expect(current()).toBe(1);
    page.querySelector<HTMLButtonElement>('.carousel-prev')!.click(); fixture.detectChanges(); expect(current()).toBe(0);
    page.querySelector<HTMLButtonElement>('.carousel-prev')!.click(); fixture.detectChanges(); expect(current()).toBe(2);
    page.querySelectorAll<HTMLButtonElement>('.dot')[1].click(); fixture.detectChanges(); expect(current()).toBe(1);
    press('ArrowRight'); expect(current()).toBe(2); press('Home'); expect(current()).toBe(0);
    press('End'); expect(current()).toBe(2); press('ArrowLeft'); expect(current()).toBe(1);
    expect(page.querySelectorAll('.slide[inert]').length).toBe(2);
    expect(page.querySelector('.counter')!.textContent).toContain('2 / 3');
  });

  it('hides controls for a single photo', () => {
    const { fixture, page, http } = setup({ roomId: 'r' });
    http.expectOne(`${api}/rooms/r/photos`).flush([photo('a', 0)]); fixture.detectChanges();
    expect(page.querySelectorAll('img').length).toBe(1);
    expect(page.querySelector('.carousel-next')).toBeNull(); expect(page.querySelector('.dots')).toBeNull();
  });

  it.each([[[] as unknown[], 200], [{}, 404]])('falls back to the labelled referential image when the gallery is %j (%s)', (body, status) => {
    const { fixture, page, http } = setup({ fallback: { src: '/images/home/hotel-dunas.webp', alt: 'Referencial' } });
    http.expectOne(`${api}/photos`).flush(body, { status, statusText: 'x' }); fixture.detectChanges();
    expect(page.querySelector('img')!.getAttribute('src')).toBe('/images/home/hotel-dunas.webp');
    expect(page.textContent).toContain('Imagen referencial'); expect(page.querySelector('section')).toBeNull();
  });

  it('renders nothing without photos or fallback and ignores non-API URLs', () => {
    const { fixture, page, http } = setup();
    http.expectOne(`${api}/photos`).flush([{ ...photo('a', 0), url: 'https://evil.test/x.png' }]); fixture.detectChanges();
    expect(page.querySelector('img')).toBeNull(); expect(page.querySelector('.placeholder')).toBeNull();
  });
});

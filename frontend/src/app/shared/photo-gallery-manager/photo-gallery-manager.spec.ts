import { TestBed } from '@angular/core/testing';
import { provideHttpClient, HttpEventType } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PhotoGalleryManager } from './photo-gallery-manager';
import { PhotoView } from '../../core/models/photo';

describe('PhotoGalleryManager', () => {
  const admin = 'http://localhost:8080/api/v1/admin/hotel/photos';
  const photo = (id: string, position = 0): PhotoView => ({ id, position, isCover: position === 0,
    url: `/api/v1/admin/hotel/photos/${id}/content`, mimeType: 'image/png', sizeBytes: 10, width: 2, height: 2, altText: '', createdAt: '' });
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [PhotoGalleryManager], providers: [provideHttpClient(), provideHttpClientTesting()] });
    Object.defineProperty(URL, 'createObjectURL', { configurable: true,
      value: vi.fn((value: Blob & { tag?: string }) => value.tag ? `blob:${value.tag}` : 'blob:preview') });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
  });
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  function setup(photos: PhotoView[] = [], open = true) {
    const fixture = TestBed.createComponent(PhotoGalleryManager);
    fixture.componentRef.setInput('hotelId', 'h'); fixture.detectChanges();
    const page: HTMLElement = fixture.nativeElement;
    const http = TestBed.inject(HttpTestingController);
    // Admin read, followed by the JWT-protected content of each photo.
    const read = (photos: PhotoView[], url = admin) => {
      http.expectOne(request => request.method === 'GET' && request.url === url).flush(photos);
      http.match(request => request.url.endsWith('/content')).forEach(request =>
        request.flush(Object.assign(new Blob(['x']), { tag: request.request.url.split('/').at(-2) })));
      fixture.detectChanges();
    };
    const click = (text: string) => {
      const button = Array.from(page.querySelectorAll('button')).find(button => button.textContent?.trim() === text)!;
      button.click(); fixture.detectChanges();
    };
    if (open) { click('Gestionar fotografías'); read(photos); }
    const select = (files: File[]) => {
      const input = page.querySelector<HTMLInputElement>('input[type=file]')!;
      Object.defineProperty(input, 'files', { configurable: true, value: files });
      input.dispatchEvent(new Event('change')); fixture.detectChanges();
    };
    return { fixture, page, http, click, select, read };
  }
  it('loads lazily, prevents duplicate reads and shows empty state', () => {
    const { page, http, click, read } = setup([], false);
    http.expectNone(admin); click('Gestionar fotografías'); click('Actualizar galería');
    read([]);
    expect(page.textContent).toContain('Todavía no hay fotografías');
    expect(page.textContent).toContain('0 / 15');
  });
  it('serializes uploads, deduplicates selection, shows progress and revokes previews', () => {
    const { select, page, http, fixture, read } = setup();
    const a = new File(['a'], 'a.png', { type: 'image/png' });
    const b = new File(['b'], 'b.webp', { type: 'image/webp' });
    select([a, a, b]);
    const first = http.expectOne(admin);
    expect(first.request.body.get('file')).toBe(a);
    fixture.componentRef.setInput('label', 'Hotel renombrado'); fixture.detectChanges();
    expect(first.cancelled).toBe(false);
    select([a]); http.expectNone(admin);
    first.event({ type: HttpEventType.UploadProgress, loaded: 5, total: 10 }); fixture.detectChanges();
    expect(page.querySelector('progress')?.value).toBe(50);
    first.flush(photo('a'));
    const second = http.expectOne(admin); expect(second.request.body.get('file')).toBe(b); second.flush(photo('b', 1));
    read([photo('a'), photo('b', 1)]);
    expect(page.querySelectorAll('.photos li img').length).toBe(2);
    expect(page.textContent).toContain('Archivo repetido');
    fixture.destroy(); expect(URL.revokeObjectURL).toHaveBeenCalledTimes(4);
  });
  it('validates format, size and remaining quota before upload', () => {
    const { select, http, page, read } = setup(Array.from({ length: 14 }, (_, i) => photo(String(i), i)));
    select([new File(['x'], 'x.svg', { type: 'image/svg+xml' }),
      new File([new Uint8Array(5242881)], 'large.png', { type: 'image/png' }),
      new File(['a'], 'a.png', { type: 'image/png' }), new File(['b'], 'b.png', { type: 'image/png' })]);
    http.expectOne(admin).flush(photo('new', 14)); read(Array.from({ length: 15 }, (_, i) => photo(String(i), i)));
    expect(page.textContent).toContain('Selecciona JPG'); expect(page.textContent).toContain('entre 1 byte y 5 MB');
    expect(page.textContent).toContain('Límite de 15'); expect(page.querySelector<HTMLInputElement>('input')!.disabled).toBe(true);
  });
  it('does not blindly retry an ambiguous upload and stops the remaining queue', () => {
    const { select, http, page, read } = setup();
    select(['a', 'b'].map(name => new File([name], name + '.png', { type: 'image/png' })));
    http.expectOne(admin).error(new ProgressEvent('error'));
    read([photo('already-saved')]);
    http.expectNone(admin); expect(page.textContent).toContain('No enviada'); expect(page.textContent).toContain('respuesta perdida');
  });
  it('requires confirmation, preserves cover until server response and refreshes deletion', () => {
    const { click, http, page, read } = setup([photo('a'), photo('b', 1)]);
    click('Eliminar'); http.expectNone(admin + '/a'); click('Conservar fotografía'); http.expectNone(admin + '/a');
    click('Eliminar'); click('Confirmar eliminación'); click('Confirmar eliminación');
    http.expectOne(admin + '/a').flush(null);
    read([photo('b')]);
    expect(page.querySelectorAll('.photos li').length).toBe(1); expect(page.querySelector('.cover')).not.toBeNull();
  });
  it('sends the complete reordered list, locks concurrent actions and preserves server cover', () => {
    const { page, http, click, read } = setup([photo('a'), photo('b', 1)]);
    click('Después →'); click('Eliminar');
    const request = http.expectOne(admin + '/order'); expect(request.request.body).toEqual({ photoIds: ['b', 'a'] });
    request.flush([]); read([{ ...photo('b'), isCover: false }, { ...photo('a', 1), isCover: true }]);
    expect(page.querySelector('li img')?.getAttribute('src')).toBe('blob:b');
    click('Elegir portada'); http.expectOne(admin + '/b/cover').flush(photo('b'));
    read([photo('b'), photo('a', 1)]);
    expect(page.querySelector('.photos li .cover')).not.toBeNull();
  });
  it.each([404, 409])('refreshes stale mutations on %s without optimistic data loss', status => {
    const { click, http, page, read } = setup([photo('a'), photo('b', 1)]);
    click('Después →'); http.expectOne(admin + '/order').flush({}, { status, statusText: 'Conflict' });
    read([photo('a')]);
    expect(page.querySelectorAll('.photos li').length).toBe(1); expect(page.querySelector('[role=alert]')).not.toBeNull();
  });
  it.each([401, 403, 404, 500])('blocks mutations when reading fails with %s', status => {
    const { click, http, fixture, page, read } = setup([], false);
    click('Gestionar fotografías'); http.expectOne(admin).flush({ message: 'SECRET' }, { status, statusText: 'Rejected' }); fixture.detectChanges();
    expect(page.querySelector<HTMLInputElement>('input')!.disabled).toBe(true);
    expect(page.textContent).not.toContain('SECRET'); expect(page.textContent).not.toContain('Todavía no hay');
    if (status === 404) expect(page.textContent).toContain('no te pertenece');
    click('Actualizar galería'); read([]);
    expect(page.querySelector<HTMLInputElement>('input')!.disabled).toBe(false);
  });
  it('reads room galleries through the owner-only API and cancels stale reads on input change', () => {
    const { fixture, click, http, page } = setup([], false);
    fixture.componentRef.setInput('hotelId', ''); fixture.componentRef.setInput('roomId', 'r'); fixture.detectChanges();
    click('Gestionar fotografías'); http.expectNone('http://localhost:8080/api/v1/admin/hotel');
    const pending = http.expectOne('http://localhost:8080/api/v1/admin/hotel/rooms/r/photos');
    fixture.componentRef.setInput('roomId', 'other'); fixture.detectChanges();
    expect(pending.cancelled).toBe(true); expect(page.textContent).toContain('/ 10');
    expect(page.textContent).toContain('Gestionar fotografías');
  });
  it('loads protected images and keeps tiles usable when one image fails', () => {
    const { fixture, click, http, page } = setup([], false);
    click('Gestionar fotografías');
    http.expectOne(admin).flush([photo('a'), photo('b', 1)]);
    http.expectOne(admin + '/a/content').flush(Object.assign(new Blob(['x']), { tag: 'a' }));
    http.expectOne(admin + '/b/content').flush(null, { status: 404, statusText: 'Missing' }); fixture.detectChanges();
    expect(page.querySelector('li img')?.getAttribute('src')).toBe('blob:a');
    expect(page.querySelector('.image-placeholder')).not.toBeNull();
    expect(page.querySelector<HTMLInputElement>('input')!.disabled).toBe(false);
    click('Actualizar galería'); http.expectOne(admin).flush([]);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:a');
  });
});

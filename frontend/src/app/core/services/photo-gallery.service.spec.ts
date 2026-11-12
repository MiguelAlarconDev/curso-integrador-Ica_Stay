import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PhotoGalleryService } from './photo-gallery.service';
import { authInterceptor } from '../interceptors/auth.interceptor';
import { AuthService } from './auth.service';
import { PhotoView } from '../models/photo';

describe('PhotoGalleryService', () => {
  let service: PhotoGalleryService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(),
      { provide: AuthService, useValue: { token: () => 'test-jwt', logout: vi.fn() } }] });
    service = TestBed.inject(PhotoGalleryService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it.each([undefined, 'room/id'])('uses real endpoints and JWT for %s', roomId => {
    const suffix = roomId ? '/rooms/room%2Fid' : '';
    const admin = `http://localhost:8080/api/v1/admin/hotel${suffix}/photos`;
    service.list('hotel/id', roomId).subscribe();
    http.expectOne(`http://localhost:8080/api/v1/hotels/hotel%2Fid${suffix}/photos`).flush([]);
    const file = new File(['bytes'], 'photo.png', { type: 'image/png' });
    service.upload(file, roomId).subscribe();
    const upload = http.expectOne(admin);
    expect(upload.request.method).toBe('POST');
    expect(upload.request.headers.get('Authorization')).toBe('Bearer test-jwt');
    expect(upload.request.headers.has('Content-Type')).toBe(false);
    expect(upload.request.body.get('file')).toBe(file);
    expect(upload.request.reportProgress).toBe(true); upload.flush({});
    service.reorder(['b', 'a'], roomId).subscribe();
    const order = http.expectOne(admin + '/order');
    expect(order.request.method).toBe('PUT'); expect(order.request.body).toEqual({ photoIds: ['b', 'a'] }); order.flush([]);
    service.cover('a', roomId).subscribe();
    const cover = http.expectOne(admin + '/a/cover'); expect(cover.request.method).toBe('PUT'); expect(cover.request.body).toBeNull(); cover.flush({});
    service.delete('a', roomId).subscribe();
    const deletion = http.expectOne(admin + '/a'); expect(deletion.request.method).toBe('DELETE'); deletion.flush(null);
    service.adminList(roomId).subscribe();
    const list = http.expectOne(admin); expect(list.request.method).toBe('GET');
    expect(list.request.headers.get('Authorization')).toBe('Bearer test-jwt'); list.flush([]);
    service.adminImage({ url: `/api/v1/admin/hotel${roomId ? '/rooms/room%2Fid' : ''}/photos/p/content` } as PhotoView).subscribe();
    const image = http.expectOne(admin + '/p/content');
    expect(image.request.responseType).toBe('blob'); expect(image.request.headers.get('Authorization')).toBe('Bearer test-jwt');
    image.flush(new Blob(['x']));
  });
  it('rejects unexpected admin image URLs without a request', () => {
    const error = vi.fn();
    service.adminImage({ url: 'https://other.test/api/v1/admin/hotel/photos/p/content' } as PhotoView).subscribe({ error });
    service.adminImage({ url: '/api/v1/hotels/h/photos/p/content' } as PhotoView).subscribe({ error });
    expect(error).toHaveBeenCalledTimes(2);
  });
  it('shares public reads between consumers and does not cache errors', () => {
    const url = 'http://localhost:8080/api/v1/hotels/h/photos';
    const covers: unknown[] = [];
    service.publicList('h').subscribe(); service.publicCover('h').subscribe(cover => covers.push(cover));
    http.expectOne(url).flush([{ id: 'a', isCover: false }, { id: 'b', isCover: true }]);
    service.publicList('h').subscribe(); http.expectNone(url);
    expect(covers).toEqual([{ id: 'b', isCover: true }]);
    service.publicCover('missing').subscribe(cover => covers.push(cover));
    http.expectOne('http://localhost:8080/api/v1/hotels/missing/photos').flush({}, { status: 404, statusText: 'Not Found' });
    expect(covers.at(-1)).toBeNull();
    service.publicList('missing').subscribe({ error: () => undefined });
    http.expectOne('http://localhost:8080/api/v1/hotels/missing/photos').flush([]);
  });
  it('resolves backend-relative images and rejects arbitrary origins', () => {
    expect(service.imageUrl({ url: '/api/v1/hotels/h/photos/p/content' } as PhotoView)).toBe('http://localhost:8080/api/v1/hotels/h/photos/p/content');
    expect(service.imageUrl({ url: 'https://other.test/image' } as PhotoView)).toBe('');
  });
});

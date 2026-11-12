import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of, shareReplay, throwError } from 'rxjs';
import { API_BASE_URL } from '../api.config';
import { PhotoView } from '../models/photo';

const PUBLIC_CACHE_MS = 60_000;

@Injectable({ providedIn: 'root' })
export class PhotoGalleryService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL).replace(/\/$/, '');
  private readonly publicCache = new Map<string, { at: number; photos: Observable<PhotoView[]> }>();

  private admin(roomId?: string) {
    return `${this.base}/api/v1/admin/hotel${roomId ? `/rooms/${encodeURIComponent(roomId)}` : ''}/photos`;
  }
  list(hotelId: string, roomId?: string) {
    return this.http.get<PhotoView[]>(`${this.base}/api/v1/hotels/${encodeURIComponent(hotelId)}${roomId ? `/rooms/${encodeURIComponent(roomId)}` : ''}/photos`);
  }
  /** Shared public read so covers, carousels and detail pages never repeat the same request. Errors are not cached. */
  publicList(hotelId: string, roomId?: string): Observable<PhotoView[]> {
    const key = `${hotelId}/${roomId ?? ''}`;
    const cached = this.publicCache.get(key);
    if (cached && Date.now() - cached.at < PUBLIC_CACHE_MS) return cached.photos;
    const photos = this.list(hotelId, roomId).pipe(
      catchError(error => { this.publicCache.delete(key); return throwError(() => error); }),
      shareReplay({ bufferSize: 1, refCount: true }),
    );
    this.publicCache.set(key, { at: Date.now(), photos });
    return photos;
  }
  /** Cover for public cards; a missing or hidden gallery resolves to null so callers keep their fallback. */
  publicCover(hotelId: string): Observable<PhotoView | null> {
    return this.publicList(hotelId).pipe(map(photos => photos.find(photo => photo.isCover) ?? photos[0] ?? null), catchError(() => of(null)));
  }
  /** Owner-only read: also returns galleries of inactive hotels and rooms. */
  adminList(roomId?: string) {
    return this.http.get<PhotoView[]>(this.admin(roomId));
  }
  /** Admin content requires the JWT, so it is fetched through HttpClient instead of a plain img src. */
  adminImage(photo: PhotoView) {
    if (!/^\/api\/v1\/admin\/hotel\/(rooms\/[^/]+\/)?photos\/[^/]+\/content$/.test(photo.url)) {
      return throwError(() => new Error('Unexpected photo URL'));
    }
    return this.http.get(this.base + photo.url, { responseType: 'blob' });
  }
  upload(file: File, roomId?: string) {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<PhotoView>(this.admin(roomId), body, { observe: 'events', reportProgress: true });
  }
  delete(id: string, roomId?: string) {
    return this.http.delete<void>(`${this.admin(roomId)}/${encodeURIComponent(id)}`);
  }
  cover(id: string, roomId?: string) {
    return this.http.put<PhotoView>(`${this.admin(roomId)}/${encodeURIComponent(id)}/cover`, null);
  }
  reorder(photoIds: string[], roomId?: string) {
    return this.http.put<PhotoView[]>(`${this.admin(roomId)}/order`, { photoIds });
  }
  imageUrl(photo: PhotoView): string {
    // PhotoView contains an API-relative path, never an arbitrary external URL.
    return photo.url.startsWith('/api/v1/hotels/') ? this.base + photo.url : '';
  }
}

import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { switchMap, tap } from 'rxjs';
import { PhotoView } from '../../core/models/photo';
import { PhotoGalleryService } from '../../core/services/photo-gallery.service';

export interface CoverFallback { src: string; alt: string; }

/** Real public cover when the hotel has one; otherwise the referential image, explicitly labelled. */
@Component({
  selector: 'app-hotel-cover',
  template: `
    @if (cover(); as photo) {
      <img [src]="service.imageUrl(photo)" [alt]="photo.altText || 'Portada de ' + name()" [attr.width]="photo.width" [attr.height]="photo.height" loading="lazy" decoding="async">
    } @else if (loaded()) {
      <img [src]="fallback().src" [alt]="fallback().alt" loading="lazy" decoding="async">
      <span class="badge">Imagen referencial</span>
    }`,
  styles: `
    :host { display: block; position: relative; overflow: hidden; background: var(--ica-sand); }
    img { display: block; width: 100%; height: 100%; object-fit: cover; }
    .badge { position: absolute; left: .6rem; bottom: .6rem; padding: .15rem .55rem; border-radius: 1rem; background: rgb(250 247 242 / .9);
      color: var(--ica-green); font-size: .68rem; letter-spacing: .06em; text-transform: uppercase; }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HotelCover {
  readonly hotelId = input.required<string>();
  readonly name = input.required<string>();
  readonly fallback = input.required<CoverFallback>();
  protected readonly service = inject(PhotoGalleryService);
  protected readonly cover = signal<PhotoView | null>(null);
  protected readonly loaded = signal(false);

  constructor() {
    toObservable(this.hotelId).pipe(
      tap(() => { this.cover.set(null); this.loaded.set(false); }),
      switchMap(id => this.service.publicCover(id)), takeUntilDestroyed(),
    ).subscribe(photo => { this.cover.set(photo && this.service.imageUrl(photo) ? photo : null); this.loaded.set(true); });
  }
}

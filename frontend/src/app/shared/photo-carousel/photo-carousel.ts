import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap, tap } from 'rxjs';
import { PhotoView } from '../../core/models/photo';
import { PhotoGalleryService } from '../../core/services/photo-gallery.service';

export interface CarouselFallback { src: string; alt: string; }

/** Public, read-only gallery. Hidden or failing galleries fall back to the referential image (or nothing). */
@Component({
  selector: 'app-photo-carousel',
  templateUrl: './photo-carousel.html',
  styleUrl: './photo-carousel.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PhotoCarousel {
  readonly hotelId = input.required<string>();
  readonly roomId = input<string>();
  readonly label = input.required<string>();
  readonly fallback = input<CarouselFallback | null>(null);
  protected readonly service = inject(PhotoGalleryService);
  protected readonly photos = signal<PhotoView[]>([]);
  protected readonly loaded = signal(false);
  protected readonly index = signal(0);
  protected readonly total = computed(() => this.photos().length);

  constructor() {
    toObservable(computed(() => ({ hotel: this.hotelId(), room: this.roomId() }))).pipe(
      tap(() => { this.loaded.set(false); this.photos.set([]); this.index.set(0); }),
      switchMap(({ hotel, room }) => this.service.publicList(hotel, room).pipe(catchError(() => of([] as PhotoView[])))),
      takeUntilDestroyed(),
    ).subscribe(photos => {
      this.photos.set(photos.filter(photo => this.service.imageUrl(photo)));
      this.loaded.set(true);
    });
  }

  protected go(target: number): void {
    const total = this.total();
    if (total > 1) this.index.set((target + total) % total);
  }
  protected key(event: KeyboardEvent): void {
    const targets: Record<string, number> = { ArrowLeft: this.index() - 1, ArrowRight: this.index() + 1, Home: 0, End: this.total() - 1 };
    if (!(event.key in targets) || this.total() < 2) return;
    event.preventDefault(); this.go(targets[event.key]);
  }
  protected alt(photo: PhotoView, position: number): string {
    return photo.altText || `${this.label()}, fotografía ${position + 1} de ${this.total()}`;
  }
}

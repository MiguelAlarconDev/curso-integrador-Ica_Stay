import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, distinctUntilChanged, map, of, Subject, switchMap, tap, merge } from 'rxjs';
import { HotelService } from '../../core/services/hotel.service';
import { HotelPage } from '../../core/models/hotel';
import { SiteHeader } from '../../shared/site-header/site-header';
import { SiteFooter } from '../../shared/site-footer/site-footer';
import { CoverFallback, HotelCover } from '../../shared/hotel-cover/hotel-cover';

// Referential images, shown only while a hotel has no public cover photo.
const FALLBACKS: CoverFallback[] = [
  { src: '/images/home/hotel-vinedos.webp', alt: 'Imagen referencial: botellas de vino sobre una barrica entre vides' },
  { src: '/images/home/hotel-dunas.webp', alt: 'Imagen referencial: piscina con palmeras y dunas al fondo' },
  { src: '/images/home/hotel-ciudad.webp', alt: 'Imagen referencial: patio de hotel con piscina y vegetación' },
];

@Component({
  selector: 'app-hotel-catalog', imports: [RouterLink, ReactiveFormsModule, SiteHeader, SiteFooter, HotelCover],
  templateUrl: './catalog.html', styleUrls: ['./hotels.css', './catalog.css'], changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HotelCatalog implements OnInit {
  private readonly service = inject(HotelService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly retryEvent = new Subject<void>();
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly result = signal<HotelPage | null>(null);
  protected readonly query = signal({ name: '', city: '', page: 0 });
  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: Validators.maxLength(180) }),
    city: new FormControl('', { nonNullable: true, validators: Validators.maxLength(120) }),
  });

  ngOnInit(): void {
    const changes = this.route.queryParamMap.pipe(map(params => {
      const raw = params.get('page') ?? '0';
      const page = /^\d+$/.test(raw) && Number(raw) <= Math.floor(2147483647 / 20) ? Number(raw) : 0;
      return { name: (params.get('name') ?? '').trim(), city: (params.get('city') ?? '').trim(), page };
    }), distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)), tap(query => {
      this.query.set(query); this.form.reset({ name: query.name, city: query.city });
    }));
    merge(changes, this.retryEvent.pipe(map(() => this.query()))).pipe(
      tap(() => { this.loading.set(true); this.error.set(''); }),
      switchMap(query => {
        if (query.name.length > 180 || query.city.length > 120) { this.error.set('Nombre: máximo 180 caracteres. Ciudad: máximo 120.'); return of(null); }
        return this.service.list(query.page, query).pipe(catchError(error => {
          this.error.set(error.status === 0 ? 'No pudimos conectar con el servidor.' : 'No pudimos cargar los hoteles. Inténtalo de nuevo.');
          return of(null);
        }));
      }), takeUntilDestroyed(this.destroyRef),
    ).subscribe(result => { this.result.set(result); this.loading.set(false); });
  }
  protected apply(): void {
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    this.navigate({ name: value.name.trim(), city: value.city.trim(), page: 0 });
  }
  protected clear(): void { this.form.reset({ name: '', city: '' }); this.navigate({ name: '', city: '', page: 0 }); }
  protected page(page: number): void {
    if (this.loading() || page < 0 || page >= (this.result()?.totalPages ?? 0)) return;
    this.navigate({ ...this.query(), page });
  }
  protected readonly skeletons = [1, 2, 3];
  protected fallback(index: number): CoverFallback { return FALLBACKS[index % FALLBACKS.length]; }
  protected retry(): void { if (!this.loading()) this.retryEvent.next(); }
  private navigate(query: { name: string; city: string; page: number }): void {
    void this.router.navigate(['/hotels'], { queryParams: { name: query.name || null, city: query.city || null, page: query.page } });
  }
}

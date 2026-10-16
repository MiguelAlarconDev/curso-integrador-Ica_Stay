import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, distinctUntilChanged, map, of, Subject, switchMap, tap, merge } from 'rxjs';
import { HotelService } from '../../core/services/hotel.service';
import { HotelPage } from '../../core/models/hotel';
import { SiteHeader } from '../../shared/site-header/site-header';
import { SiteFooter } from '../../shared/site-footer/site-footer';

@Component({
  selector: 'app-hotel-catalog', imports: [RouterLink, ReactiveFormsModule, SiteHeader, SiteFooter],
  templateUrl: './catalog.html', styleUrl: './hotels.css', changeDetection: ChangeDetectionStrategy.OnPush,
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
  protected retry(): void { if (!this.loading()) this.retryEvent.next(); }
  private navigate(query: { name: string; city: string; page: number }): void {
    void this.router.navigate(['/hotels'], { queryParams: { name: query.name || null, city: query.city || null, page: query.page } });
  }
}

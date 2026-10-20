import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, distinctUntilChanged, map, merge, of, Subject, switchMap, tap } from 'rxjs';
import { SiteHeader } from '../../shared/site-header/site-header';
import { AuthService } from '../../core/services/auth.service';
import { SuperadminHotelService } from '../../core/services/superadmin-hotel.service';
import { SuperadminHotelPage } from '../../core/models/superadmin-hotel';

@Component({
  selector: 'app-superadmin-hotels', imports: [SiteHeader, RouterLink, ReactiveFormsModule],
  templateUrl: './hotels.html', styleUrl: './hotels.css', changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SuperadminHotels implements OnInit {
  private readonly service = inject(SuperadminHotelService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  protected readonly created = this.route.snapshot.queryParamMap.get('created') === '1';
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly retryEvent = new Subject<void>();
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly result = signal<SuperadminHotelPage | null>(null);
  protected readonly query = signal({ name: '', status: '', page: 0 });
  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: Validators.maxLength(180) }),
    status: new FormControl('', { nonNullable: true, validators: Validators.pattern(/^(ACTIVE|INACTIVE)?$/) }),
  });

  constructor() {
    effect(() => {
      if (!this.auth.session()) {
        this.result.set(null);
        void this.router.navigateByUrl('/login');
      }
    });
  }

  ngOnInit(): void {
    const changes = this.route.queryParamMap.pipe(map(params => {
      const raw = params.get('page') ?? '0';
      const page = /^\d+$/.test(raw) && Number(raw) <= Math.floor(2147483647 / 20) ? Number(raw) : 0;
      return { name: (params.get('name') ?? '').trim(), status: params.get('status') ?? '', page };
    }), distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)), tap(query => {
      this.query.set(query); this.form.reset({ name: query.name, status: query.status });
    }));
    merge(changes, this.retryEvent.pipe(map(() => this.query()))).pipe(
      tap(() => { this.loading.set(true); this.error.set(''); this.result.set(null); }),
      switchMap(query => {
        if (query.name.length > 180 || !['', 'ACTIVE', 'INACTIVE'].includes(query.status)) {
          this.error.set('Revisa los filtros: nombre de hasta 180 caracteres y estado activo o inactivo.');
          return of(null);
        }
        return this.service.list(query).pipe(catchError(error => {
          this.error.set(error.status === 403 ? 'No tienes permiso para consultar los hoteles.'
            : 'No pudimos cargar los hoteles. Inténtalo de nuevo.');
          return of(null);
        }));
      }), takeUntilDestroyed(this.destroyRef),
    ).subscribe(result => { this.result.set(result); this.loading.set(false); });
  }

  protected apply(): void {
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    this.navigate({ name: value.name.trim(), status: value.status, page: 0 });
  }
  protected clear(): void {
    this.form.reset({ name: '', status: '' });
    this.navigate({ name: '', status: '', page: 0 });
  }
  protected page(page: number): void {
    if (this.loading() || page < 0 || (page !== 0 && page >= (this.result()?.totalPages ?? 0))) return;
    this.navigate({ ...this.query(), page });
  }
  protected retry(): void { if (!this.loading()) this.retryEvent.next(); }
  private navigate(query: { name: string; status: string; page: number }): void {
    void this.router.navigate(['/superadmin/hotels'], {
      queryParams: { name: query.name || null, status: query.status || null, page: query.page },
    });
  }
}

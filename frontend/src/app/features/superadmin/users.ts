import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, distinctUntilChanged, map, merge, of, Subject, switchMap, tap } from 'rxjs';
import { SiteHeader } from '../../shared/site-header/site-header';
import { AuthService } from '../../core/services/auth.service';
import { SuperadminUserService } from '../../core/services/superadmin-user.service';
import { SuperadminUserPage } from '../../core/models/superadmin-user';

@Component({
  selector: 'app-superadmin-users', imports: [SiteHeader, RouterLink, ReactiveFormsModule],
  templateUrl: './users.html', styleUrls: ['./hotels.css', './users.css'], changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SuperadminUsers implements OnInit {
  private readonly service = inject(SuperadminUserService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly retryEvent = new Subject<void>();
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly result = signal<SuperadminUserPage | null>(null);
  protected readonly query = signal({ q: '', role: '', status: '', page: 0 });
  protected readonly form = new FormGroup({
    q: new FormControl('', { nonNullable: true, validators: Validators.maxLength(254) }),
    role: new FormControl('', { nonNullable: true, validators: Validators.pattern(/^(USER|HOTEL_ADMIN|SUPER_ADMIN)?$/) }),
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
      return { q: (params.get('q') ?? '').trim(), role: params.get('role') ?? '', status: params.get('status') ?? '', page };
    }), distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)), tap(query => {
      this.query.set(query); this.form.reset({ q: query.q, role: query.role, status: query.status });
    }));
    merge(changes, this.retryEvent.pipe(map(() => this.query()))).pipe(
      tap(() => { this.loading.set(true); this.error.set(''); this.result.set(null); }),
      switchMap(query => {
        if (!['', 'USER', 'HOTEL_ADMIN', 'SUPER_ADMIN'].includes(query.role) || query.q.length > 254 || !['', 'ACTIVE', 'INACTIVE'].includes(query.status)) {
          this.error.set('Revisa los filtros: búsqueda de hasta 254 caracteres, rol y estado válidos.');
          return of(null);
        }
        return this.service.list(query).pipe(catchError(error => {
          this.error.set(error.status === 403 ? 'No tienes permiso para consultar los usuarios.'
            : 'No pudimos cargar los usuarios. Inténtalo de nuevo.');
          return of(null);
        }));
      }), takeUntilDestroyed(this.destroyRef),
    ).subscribe(result => { this.result.set(result); this.loading.set(false); });
  }

  protected apply(): void {
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    this.navigate({ q: value.q.trim(), role: value.role, status: value.status, page: 0 });
  }
  protected clear(): void {
    this.form.reset({ q: '', role: '', status: '' });
    this.navigate({ q: '', role: '', status: '', page: 0 });
  }
  protected page(page: number): void {
    if (this.loading() || page < 0 || (page !== 0 && page >= (this.result()?.totalPages ?? 0))) return;
    this.navigate({ ...this.query(), page });
  }
  protected retry(): void { if (!this.loading()) this.retryEvent.next(); }
  private navigate(query: { q: string; role: string; status: string; page: number }): void {
    void this.router.navigate(['/superadmin/users'], {
      queryParams: { q: query.q || null, role: query.role || null, status: query.status || null, page: query.page },
    });
  }
}

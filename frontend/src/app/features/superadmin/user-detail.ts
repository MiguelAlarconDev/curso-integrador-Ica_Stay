import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, finalize, skip, takeUntil, distinctUntilChanged, map, merge, of, Subject, switchMap, tap } from 'rxjs';
import { SiteHeader } from '../../shared/site-header/site-header';
import { AuthService } from '../../core/services/auth.service';
import { SuperadminUserService } from '../../core/services/superadmin-user.service';
import { SuperadminUserDetail } from '../../core/models/superadmin-user';
@Component({
  selector: 'app-superadmin-user-detail', imports: [SiteHeader, RouterLink],
  templateUrl: './user-detail.html', styleUrl: './hotels.css', changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SuperadminUserDetailPage implements OnInit {
  private readonly service = inject(SuperadminUserService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly retries = new Subject<void>();
  protected readonly busy = signal(false);
  protected readonly pendingStatus = signal<'ACTIVE' | 'INACTIVE' | null>(null);
  protected readonly message = signal('');
  protected readonly mutationError = signal('');
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly user = signal<SuperadminUserDetail | null>(null);
  protected readonly filters = this.route.snapshot.queryParams;
  constructor() {
    effect(() => { if (!this.auth.session()) { this.user.set(null); void this.router.navigateByUrl('/login'); } });
  }
  ngOnInit(): void {
    merge(this.route.paramMap.pipe(map(p => p.get('id') ?? ''), distinctUntilChanged()),
      this.retries.pipe(map(() => this.route.snapshot.paramMap.get('id') ?? ''))).pipe(
      tap(() => { this.pendingStatus.set(null); this.message.set(''); this.mutationError.set(''); this.loading.set(true); this.error.set(''); this.user.set(null); }),
      switchMap(id => this.service.get(id).pipe(catchError(error => {
        this.error.set(error.status === 404 ? 'El usuario no existe o ya no está disponible.'
          : error.status === 403 ? 'No tienes permiso para consultar este usuario.'
          : error.status === 400 ? 'El identificador del usuario no es válido.'
          : 'No pudimos cargar el usuario. Inténtalo de nuevo.');
        return of(null);
      }))), takeUntilDestroyed(this.destroyRef),
    ).subscribe(user => { this.user.set(user); this.loading.set(false); });
  }
  protected retry(): void { if (!this.loading() && !this.busy()) this.retries.next(); }
  protected askStatus(): void {
    const user = this.user();
    if (!user || this.busy() || (user.id === this.auth.userId() && user.status === 'ACTIVE')) return;
    this.message.set(''); this.mutationError.set('');
    this.pendingStatus.set(user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE');
  }
  protected cancelStatus(): void { if (!this.busy()) this.pendingStatus.set(null); }
  protected confirmStatus(): void {
    const user = this.user(), status = this.pendingStatus();
    if (!user || !status || this.busy() || (user.id === this.auth.userId() && status === 'INACTIVE')) return;
    this.busy.set(true); this.mutationError.set(''); this.message.set('');
    this.service.changeStatus(user.id, status).pipe(takeUntil(this.route.paramMap.pipe(skip(1))),
      takeUntilDestroyed(this.destroyRef), finalize(() => this.busy.set(false))).subscribe({
      next: updated => { this.user.set(updated); this.pendingStatus.set(null); this.message.set('Estado del usuario actualizado.'); },
      error: error => {
        this.pendingStatus.set(null);
        this.mutationError.set(error.status === 400 ? 'No se permite este cambio: revisa el estado, la cuenta propia y el último SUPER_ADMIN activo.'
          : error.status === 403 ? 'No tienes permiso para cambiar este estado. Actualiza tu sesión.'
          : error.status === 404 ? 'El usuario ya no existe. Vuelve al listado.'
          : 'No pudimos cambiar el estado. Reintenta la operación.');
      },
    });
  }
}

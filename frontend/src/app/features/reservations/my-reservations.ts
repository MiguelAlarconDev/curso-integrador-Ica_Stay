import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, OnInit, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, combineLatest, distinctUntilChanged, map, merge, Observable, of, Subject, switchMap, tap } from 'rxjs';
import { ReservationService } from '../../core/services/reservation.service';
import { AuthService } from '../../core/services/auth.service';
import { UserReservationPage, UserReservationView } from '../../core/models/reservation';
import { SiteHeader } from '../../shared/site-header/site-header';
import { SiteFooter } from '../../shared/site-footer/site-footer';
import { ReservationState } from './reservation-state';
import { ReservationPayment } from './reservation-payment';

@Component({ selector: 'app-my-reservations', imports: [RouterLink, DatePipe, DecimalPipe, SiteHeader, SiteFooter, ReservationState, ReservationPayment],
  templateUrl: './my-reservations.html', styleUrl: './my-reservations.css', changeDetection: ChangeDetectionStrategy.OnPush })
export class MyReservations implements OnInit {
  private readonly service = inject(ReservationService);
  private readonly auth = inject(AuthService);
  private readonly owner = this.auth.userId();
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly refreshEvent = new Subject<void>();
  protected readonly query = signal({ id: null as string | null, page: 0 });
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly result = signal<UserReservationPage | null>(null);
  protected readonly detail = signal<UserReservationView | null>(null);

  constructor() {
    effect(() => {
      if (!this.auth.hasRole('USER') || this.owner !== this.auth.userId()) {
        this.result.set(null); this.detail.set(null);
        void this.router.navigateByUrl(this.auth.isAuthenticated() ? '/' : '/login');
      }
    });
  }
  ngOnInit(): void {
    const changes = combineLatest([this.route.paramMap, this.route.queryParamMap]).pipe(map(([params, search]) => {
      const raw = search.get('page') ?? '0';
      const page = /^\d+$/.test(raw) && Number(raw) <= Math.floor(2147483647 / 20) ? Number(raw) : 0;
      return { id: params.get('id'), page };
    }), distinctUntilChanged((a, b) => a.id === b.id && a.page === b.page), tap(query => this.query.set(query)));
    merge(changes, this.refreshEvent.pipe(map(() => this.query()))).pipe(
      tap(() => { this.loading.set(true); this.error.set(''); this.result.set(null); this.detail.set(null); }),
      switchMap(query => {
        const request: Observable<UserReservationPage | UserReservationView> = query.id ? this.service.getOwn(query.id) : this.service.listOwn(query.page);
        return request.pipe(catchError(error => {
          const messages: Record<number, string> = {
            0: 'No pudimos conectar con el servidor. Inténtalo nuevamente.',
            400: 'La consulta no es válida. Vuelve al listado de reservas.',
            401: 'Tu sesión expiró. Inicia sesión nuevamente.',
            403: 'Solo los huéspedes con rol USER pueden consultar sus reservas.',
            404: 'La reserva no existe o no está disponible para tu usuario.',
          };
          this.error.set(messages[error.status] ?? 'No pudimos consultar las reservas. Inténtalo nuevamente.');
          return of(null);
        }));
      }), takeUntilDestroyed(this.destroyRef),
    ).subscribe(value => {
      if (this.auth.hasRole('USER') && this.auth.userId() === this.owner && value) {
        if ('content' in value) this.result.set(value); else this.detail.set(value);
      }
      this.loading.set(false);
    });
  }
  protected refresh(): void { if (!this.loading()) this.refreshEvent.next(); }
  protected page(page: number): void {
    if (this.loading() || page < 0 || page >= (this.result()?.totalPages ?? 0)) return;
    void this.router.navigate(['/my-reservations'], { queryParams: { page } });
  }
}

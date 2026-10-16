import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { ReservationService } from '../../core/services/reservation.service';
import { AvailabilityQuery, RoomView } from '../../core/models/room';
import { ReservationView } from '../../core/models/reservation';
import { bookingReturn } from '../../core/auth/booking-return';

@Component({ selector: 'app-booking', imports: [ReactiveFormsModule, DecimalPipe, DatePipe],
  templateUrl: './booking.html', styleUrl: './hotels.css', changeDetection: ChangeDetectionStrategy.OnPush })
export class Booking {
  readonly room = input.required<RoomView>();
  readonly query = input.required<AvailabilityQuery>();
  readonly hotelId = input.required<string>();
  readonly hotelName = input.required<string>();
  readonly busy = output<boolean>();
  readonly refresh = output<void>();
  readonly terminal = output<void>();
  protected readonly auth = inject(AuthService);
  private readonly service = inject(ReservationService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly confirmed = new FormControl(false, { nonNullable: true });
  protected readonly sending = signal(false);
  protected readonly result = signal<ReservationView | null>(null);
  protected readonly error = signal('');
  protected readonly uncertain = signal(false);
  protected readonly conflict = signal(false);
  protected readonly nights = computed(() => (Date.parse(this.query().checkOut) - Date.parse(this.query().checkIn)) / 86400000);
  protected login(): void {
    const query = this.query();
    const tree = this.router.createUrlTree(['/hotels', this.hotelId()], { queryParams: { ...this.route.snapshot.queryParams, ...query } });
    void this.router.navigate(['/login'], { queryParams: { returnUrl: bookingReturn(this.router.serializeUrl(tree)) } });
  }
  protected submit(): void {
    if (this.sending() || this.result() || this.uncertain() || this.conflict() || !this.confirmed.value) return;
    if (!this.auth.token()) { this.login(); return; }
    if (!this.auth.hasRole('USER')) { this.error.set('Solo los huéspedes con rol USER pueden reservar.'); return; }
    this.sending.set(true); this.busy.emit(true); this.error.set('');
    this.service.create({ roomId: this.room().id, ...this.query() })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => { this.sending.set(false); this.busy.emit(false); }))
      .subscribe({ next: result => { this.result.set(result); this.terminal.emit(); }, error: error => {
        this.confirmed.setValue(false);
        switch (error.status) {
          case 400: this.error.set('Datos inválidos. Revisa las fechas y huéspedes consultando disponibilidad nuevamente.'); break;
          case 401: this.error.set('Tu sesión expiró. Inicia sesión y confirma nuevamente.'); break;
          case 403: this.error.set('Tu rol no permite crear reservas.'); break;
          case 404: this.error.set('La habitación o el hotel ya no están disponibles.'); this.conflict.set(true); break;
          case 409: this.error.set('La habitación ya no está disponible para esas fechas. Consulta nuevamente.'); this.conflict.set(true); break;
          default: this.uncertain.set(true); this.terminal.emit(); this.error.set('Resultado incierto: no podemos verificar si la reserva se creó. No vuelvas a enviarla sin verificarlo con el establecimiento.');
        }
      } });
  }
}

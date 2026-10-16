import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { PaymentResult } from '../../core/models/payment';
import { UserReservationView } from '../../core/models/reservation';
import { PaymentService } from '../../core/services/payment.service';
import { ReservationService } from '../../core/services/reservation.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-reservation-payment',
  templateUrl: './reservation-payment.html',
  styleUrl: './reservation-payment.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReservationPayment {
  readonly reservation = input.required<UserReservationView>();
  readonly updated = output<UserReservationView>();
  private readonly payments = inject(PaymentService);
  private readonly reservations = inject(ReservationService);
  private readonly auth = inject(AuthService);
  private readonly owner = this.auth.userId();
  private readonly destroyRef = inject(DestroyRef);
  private readonly tick = signal(performance.now());
  private receivedAt = performance.now();
  protected readonly expanded = signal(false);
  protected readonly busy = signal(false);
  protected readonly message = signal('');
  protected readonly error = signal('');
  protected readonly unavailable = signal(false);
  protected readonly operation = computed(() => this.payments.operation(this.reservation().id));
  protected readonly expired = computed(() => this.hasExpired(this.tick()));
  protected readonly canPay = computed(() => this.authorized() && this.reservation().status === 'PENDING_PAYMENT'
    && !this.expired() && !this.busy() && !this.operation() && !this.unavailable());

  constructor() {
    effect(() => {
      this.reservation();
      this.receivedAt = performance.now();
      this.tick.set(this.receivedAt);
    });
    const timer = setInterval(() => this.tick.set(performance.now()), 1000);
    this.destroyRef.onDestroy(() => clearInterval(timer));
  }

  protected simulate(result: PaymentResult): void {
    if (!this.canPay() || this.hasExpired(performance.now())) return;
    this.busy.set(true); this.error.set(''); this.message.set('');
    this.payments.simulate({ reservationId: this.reservation().id, result })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: response => {
          this.busy.set(false);
          if (!this.authorized()) return;
          if (response.result === 'REJECTED') {
            this.message.set('Pago simulado rechazado. La reserva sigue pendiente y conserva su vencimiento original.');
          } else {
            this.message.set('Pago simulado aprobado. Consultando la reserva confirmada…');
            this.refreshDetail();
          }
        },
        error: (error: unknown) => {
          this.busy.set(false);
          if (!this.authorized()) return;
          const status = error instanceof HttpErrorResponse ? error.status : 0;
          const messages: Record<number, string> = {
            400: 'La solicitud de simulación no es válida. Actualiza el detalle antes de continuar.',
            401: 'Tu sesión expiró. Inicia sesión nuevamente.',
            403: 'No tienes permiso para simular este pago. Solo está disponible para USER.',
            404: 'La reserva no existe o no está disponible para tu usuario.',
            409: 'La reserva ya no admite este pago. Actualiza el detalle para comprobar su estado.',
          };
          this.unavailable.set([400, 403, 404].includes(status));
          this.error.set(messages[status] ?? 'No pudimos verificar el resultado del pago. No lo reenvíes; actualiza el detalle para consultar su estado.');
        },
      });
  }

  protected refresh(): void {
    if (this.busy() || this.operation()?.pending || !this.authorized()) return;
    this.refreshDetail();
  }

  private refreshDetail(): void {
    this.busy.set(true); this.error.set('');
    this.reservations.getOwn(this.reservation().id)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.busy.set(false)))
      .subscribe({
        next: value => {
          if (!this.authorized()) return;
          this.updated.emit(value); this.error.set(''); this.unavailable.set(false);
          this.message.set(value.status === 'CONFIRMED' ? 'Reserva CONFIRMED: confirmada por el servidor.'
            : this.operation()?.needsVerification && value.status === 'PENDING_PAYMENT' && !value.holdExpired
              ? 'La reserva todavía figura pendiente. Esto no demuestra que el pago haya fallado. No reenvíes el pago; puedes volver a actualizar el detalle.'
              : 'Detalle actualizado desde el servidor.');
        },
        error: (error: HttpErrorResponse) => {
          if (!this.authorized()) return;
          this.message.set('');
          const messages: Record<number, string> = {
            400: 'No se pudo consultar esta reserva: solicitud inválida.',
            401: 'Tu sesión expiró. Inicia sesión nuevamente.',
            403: 'No tienes permiso para consultar esta reserva.',
            404: 'La reserva no existe o no está disponible para tu usuario.',
            409: 'El estado de la reserva cambió. Vuelve a actualizar el detalle.',
          };
          this.error.set(messages[error.status] ?? 'No pudimos actualizar el detalle. No repitas el pago; vuelve a consultar el estado.');
        },
      });
  }

  private authorized(): boolean { return this.auth.hasRole('USER') && this.auth.userId() === this.owner; }
  private hasExpired(at: number): boolean {
    const reservation = this.reservation();
    const expiry = Date.parse(reservation.expiresAt ?? '');
    const evaluated = Date.parse(reservation.evaluatedAt);
    return reservation.holdExpired || !Number.isFinite(expiry) || !Number.isFinite(evaluated)
      || expiry <= evaluated + Math.max(0, at - this.receivedAt);
  }
}

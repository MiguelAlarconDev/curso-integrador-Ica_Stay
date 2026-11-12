import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ReservationStatus, UserReservationView } from '../../core/models/reservation';

@Component({ selector: 'app-reservation-state', imports: [DatePipe], changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `:host { display: block; } p { margin: .4rem 0; } small { color: var(--ica-muted); }`,
  template: `<p>Estado: <strong class="badge" [class]="'badge-' + (reservation().holdExpired ? 'expired' : reservation().status.toLowerCase())">{{ labels[reservation().status] }}</strong> ({{ reservation().status }})</p>
    @if (reservation().holdExpired) { <p class="alert alert-error">Retención vencida. El estado guardado sigue siendo PENDING_PAYMENT.</p> }
    @else if (reservation().status === 'PENDING_PAYMENT') { <p class="alert alert-info">Pendiente de pago; no es una reserva confirmada.</p> }
    <small>Consultado en el servidor: {{ reservation().evaluatedAt | date:'yyyy-MM-dd HH:mm:ss':'UTC' }} UTC.</small>`,
})
export class ReservationState {
  readonly reservation = input.required<UserReservationView>();
  protected readonly labels: Record<ReservationStatus, string> = {
    PENDING_PAYMENT: 'Pendiente de pago', CONFIRMED: 'Confirmada', CANCELLED: 'Cancelada', EXPIRED: 'Expirada',
  };
}

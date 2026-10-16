import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ReservationStatus, UserReservationView } from '../../core/models/reservation';

@Component({ selector: 'app-reservation-state', imports: [DatePipe], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p>Estado: <strong>{{ labels[reservation().status] }}</strong> ({{ reservation().status }})</p>
    @if (reservation().holdExpired) { <p>Retención vencida. El estado guardado sigue siendo PENDING_PAYMENT.</p> }
    @else if (reservation().status === 'PENDING_PAYMENT') { <p>Pendiente de pago; no es una reserva confirmada.</p> }
    <small>Consultado en el servidor: {{ reservation().evaluatedAt | date:'yyyy-MM-dd HH:mm:ss':'UTC' }} UTC.</small>`,
})
export class ReservationState {
  readonly reservation = input.required<UserReservationView>();
  protected readonly labels: Record<ReservationStatus, string> = {
    PENDING_PAYMENT: 'Pendiente de pago', CONFIRMED: 'Confirmada', CANCELLED: 'Cancelada', EXPIRED: 'Expirada',
  };
}

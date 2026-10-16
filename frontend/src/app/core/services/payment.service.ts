import { inject, Injectable, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { catchError, defer, finalize, tap, throwError } from 'rxjs';
import { API_BASE_URL } from '../api.config';
import { PaymentView, SimulatePaymentRequest } from '../models/payment';
import { AuthService } from './auth.service';

interface PaymentOperation { pending: boolean; needsVerification: boolean; }

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);
  private readonly auth = inject(AuthService);
  // Keep uncertain operations blocked even if the detail component is recreated.
  private readonly operations = signal<Record<string, PaymentOperation>>({});

  operation(reservationId: string): PaymentOperation | undefined {
    return this.operations()[this.key(reservationId)];
  }

  simulate({ reservationId, result }: SimulatePaymentRequest) {
    return defer(() => {
      const key = this.key(reservationId);
      if (!this.auth.hasRole('USER') || this.operations()[key]) {
        return throwError(() => new Error('Consulta el estado de la reserva antes de continuar.'));
      }
      this.set(key, { pending: true, needsVerification: true });
      return this.http.post<PaymentView>(`${this.base}/api/v1/payments/simulate`, { reservationId, result }).pipe(
        tap(response => {
          if (!response?.id || !response.reference || response.result !== result) {
            throw new Error('Respuesta de pago no verificable.');
          }
          this.set(key, response.result === 'REJECTED' ? undefined : { pending: false, needsVerification: true });
        }),
        catchError((error: unknown) => {
          const definitive = error instanceof HttpErrorResponse && [400, 401, 403, 404].includes(error.status);
          this.set(key, definitive ? undefined : { pending: false, needsVerification: true });
          return throwError(() => error);
        }),
        finalize(() => {
          if (this.operations()[key]?.pending) this.set(key, { pending: false, needsVerification: true });
        }),
      );
    });
  }

  private key(id: string): string { return `${this.auth.userId()}:${id}`; }
  private set(key: string, value: PaymentOperation | undefined): void {
    this.operations.update(current => {
      const updated = { ...current };
      if (value) updated[key] = value; else delete updated[key];
      return updated;
    });
  }
}

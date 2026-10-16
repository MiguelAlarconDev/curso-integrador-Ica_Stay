import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../api.config';
import { CreateReservationRequest, ReservationView } from '../models/reservation';
@Injectable({ providedIn: 'root' })
export class ReservationService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);
  create({ roomId, checkIn, checkOut, guests }: CreateReservationRequest) {
    return this.http.post<ReservationView>(`${this.base}/api/v1/reservations`, { roomId, checkIn, checkOut, guests });
  }
}

import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../api.config';
import { CreateReservationRequest, ReservationView, UserReservationPage, UserReservationView } from '../models/reservation';
@Injectable({ providedIn: 'root' })
export class ReservationService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);
  listOwn(page = 0) { return this.http.get<UserReservationPage>(`${this.base}/api/v1/reservations/me`, { params: { page } }); }
  getOwn(id: string) { return this.http.get<UserReservationView>(`${this.base}/api/v1/reservations/${encodeURIComponent(id)}`); }
  create({ roomId, checkIn, checkOut, guests }: CreateReservationRequest) {
    return this.http.post<ReservationView>(`${this.base}/api/v1/reservations`, { roomId, checkIn, checkOut, guests });
  }
}

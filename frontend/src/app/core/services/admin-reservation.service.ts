import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API_BASE_URL } from '../api.config';
import { AdminReservationPage, AdminReservationView, ReservationFilters } from '../models/admin-reservation';

@Injectable({ providedIn: 'root' })
export class AdminReservationService {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(API_BASE_URL)}/api/v1/admin/hotel/reservations`;

  list(page: number, filters: ReservationFilters) {
    let params = new HttpParams().set('page', page).set('size', 20);
    for (const key of ['status', 'from', 'to'] as const) {
      if (filters[key]) params = params.set(key, filters[key]);
    }
    return this.http.get<AdminReservationPage>(this.url, { params });
  }
  get(id: string) { return this.http.get<AdminReservationView>(`${this.url}/${encodeURIComponent(id)}`); }
}

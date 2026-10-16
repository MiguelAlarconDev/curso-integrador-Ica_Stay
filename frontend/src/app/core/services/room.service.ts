import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../api.config';
import { AvailabilityQuery, RoomView } from '../models/room';

@Injectable({ providedIn: 'root' })
export class RoomService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);
  available(id: string, { checkIn, checkOut, guests }: AvailabilityQuery) {
    return this.http.get<RoomView[]>(`${this.base}/api/v1/hotels/${encodeURIComponent(id)}/rooms`, { params: { checkIn, checkOut, guests } });
  }
}

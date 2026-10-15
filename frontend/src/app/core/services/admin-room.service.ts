import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../api.config';
import { AdminRoomView, SaveRoomRequest } from '../models/admin-room';

@Injectable({ providedIn: 'root' })
export class AdminRoomService {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(API_BASE_URL)}/api/v1/admin/hotel/rooms`;

  list() { return this.http.get<AdminRoomView[]>(this.url); }
  get(id: string) { return this.http.get<AdminRoomView>(`${this.url}/${encodeURIComponent(id)}`); }
  create(request: SaveRoomRequest) { return this.http.post<AdminRoomView>(this.url, this.payload(request)); }
  update(id: string, request: SaveRoomRequest) {
    return this.http.put<AdminRoomView>(`${this.url}/${encodeURIComponent(id)}`, this.payload(request));
  }
  changeStatus(id: string, status: AdminRoomView['status']) {
    return this.http.patch<AdminRoomView>(`${this.url}/${encodeURIComponent(id)}/status`, { status });
  }
  private payload({ number, description, capacity, pricePerNight, currency }: SaveRoomRequest): SaveRoomRequest {
    return { number, description, capacity, pricePerNight, currency };
  }
}

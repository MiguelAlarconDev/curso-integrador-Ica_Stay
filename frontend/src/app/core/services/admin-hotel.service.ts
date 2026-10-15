import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../api.config';
import { HotelView, UpdateHotelRequest } from '../models/hotel';

@Injectable({ providedIn: 'root' })
export class AdminHotelService {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(API_BASE_URL)}/api/v1/admin/hotel`;

  get() { return this.http.get<HotelView>(this.url); }

  update(request: UpdateHotelRequest) {
    const { name, description, address, city } = request;
    return this.http.put<HotelView>(this.url, { name, description, address, city });
  }
}

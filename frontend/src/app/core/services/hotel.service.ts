import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../api.config';
import { HotelPage, HotelView } from '../models/hotel';

@Injectable({ providedIn: 'root' })
export class HotelService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  list(page = 0, filters: { name?: string; city?: string } = {}) {
    const params: Record<string, string | number> = { page, size: 20 };
    if (filters.name) params['name'] = filters.name;
    if (filters.city) params['city'] = filters.city;
    return this.http.get<HotelPage>(`${this.baseUrl}/api/v1/hotels`, {
      params,
    });
  }

  get(id: string) { return this.http.get<HotelView>(`${this.baseUrl}/api/v1/hotels/${encodeURIComponent(id)}`); }
}

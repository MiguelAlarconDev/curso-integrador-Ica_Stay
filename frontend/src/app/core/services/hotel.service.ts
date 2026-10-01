import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../api.config';
import { HotelPage } from '../models/hotel';

@Injectable({ providedIn: 'root' })
export class HotelService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  list() {
    return this.http.get<HotelPage>(`${this.baseUrl}/api/v1/hotels`, {
      params: { page: 0, size: 20 },
    });
  }
}

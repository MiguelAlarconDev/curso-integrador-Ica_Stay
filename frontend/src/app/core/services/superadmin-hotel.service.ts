import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../api.config';
import { CreateHotelRequest, SuperadminHotelDetail, SuperadminHotelPage, SuperadminHotelView } from '../models/superadmin-hotel';
import { UpdateHotelRequest } from '../models/hotel';

@Injectable({ providedIn: 'root' })
export class SuperadminHotelService {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(API_BASE_URL)}/api/v1/superadmin/hotels`;

  get(id: string) { return this.http.get<SuperadminHotelDetail>(`${this.url}/${encodeURIComponent(id)}`); }
  update(id: string, request: UpdateHotelRequest) {
    const { name, description, address, city } = request;
    return this.http.put<SuperadminHotelDetail>(`${this.url}/${encodeURIComponent(id)}`, { name, description, address, city });
  }
  changeStatus(id: string, status: 'ACTIVE' | 'INACTIVE') {
    return this.http.patch<SuperadminHotelDetail>(`${this.url}/${encodeURIComponent(id)}/status`, { status });
  }

  create(request: CreateHotelRequest) {
    const { name, description, address, city, admin } = request;
    return this.http.post<SuperadminHotelView>(this.url, {
      name, description, address, city, admin: { name: admin.name, email: admin.email, password: admin.password },
    });
  }

  list(query: { page: number; name: string; status: string }) {
    const params: Record<string, string | number> = { page: query.page };
    if (query.name) params['name'] = query.name;
    if (query.status) params['status'] = query.status;
    return this.http.get<SuperadminHotelPage>(this.url, { params });
  }
}

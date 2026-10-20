import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../api.config';
import { SuperadminUserPage, SuperadminUserDetail } from '../models/superadmin-user';
@Injectable({ providedIn: 'root' })
export class SuperadminUserService {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(API_BASE_URL)}/api/v1/superadmin/users`;
  changeStatus(id: string, status: 'ACTIVE' | 'INACTIVE') {
    return this.http.patch<SuperadminUserDetail>(`${this.url}/${encodeURIComponent(id)}/status`, { status });
  }
  get(id: string) { return this.http.get<SuperadminUserDetail>(`${this.url}/${encodeURIComponent(id)}`); }
  list(query: { page: number; q: string; role: string; status: string }) {
    const params: Record<string, string | number> = { page: query.page };
    if (query.q) params['q'] = query.q;
    if (query.role) params['role'] = query.role;
    if (query.status) params['status'] = query.status;
    return this.http.get<SuperadminUserPage>(this.url, { params });
  }
}

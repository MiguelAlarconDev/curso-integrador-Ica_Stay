export interface SuperadminUserView { id: string; name: string; email: string; role: 'USER' | 'HOTEL_ADMIN' | 'SUPER_ADMIN'; status: 'ACTIVE' | 'INACTIVE'; }
export interface SuperadminUserPage { content: SuperadminUserView[]; page: number; size: number; totalElements: number; totalPages: number; }
export interface SuperadminUserDetail extends SuperadminUserView {
  hotel: { id: string; name: string; status: 'ACTIVE' | 'INACTIVE' } | null;
}

export interface SuperadminHotelView {
  id: string;
  name: string;
  address: string;
  city: string;
  status: 'ACTIVE' | 'INACTIVE';
  adminUserId: string;
  adminName: string;
  adminEmail: string;
}

export interface SuperadminHotelPage {
  content: SuperadminHotelView[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SuperadminHotelDetail extends SuperadminHotelView { description: string | null; }

export interface CreateHotelRequest {
  name: string;
  description: string;
  address: string;
  city: string;
  admin: { name: string; email: string; password: string };
}

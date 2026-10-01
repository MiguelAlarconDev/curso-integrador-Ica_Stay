export interface HotelView {
  id: string;
  name: string;
  description: string | null;
  address: string;
  city: string;
}

export interface HotelPage {
  content: HotelView[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

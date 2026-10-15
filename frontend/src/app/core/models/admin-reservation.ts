export type ReservationStatus = 'PENDING_PAYMENT' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED';

export interface AdminReservationView {
  id: string;
  checkIn: string;
  checkOut: string;
  guests: number;
  status: ReservationStatus;
  expiresAt: string | null;
  nightlyPriceSnapshot: number;
  totalAmount: number;
  currency: string;
  createdAt: string;
  roomId: string;
  roomNumber: string;
  guestUserId: string;
  guestName: string;
  guestEmail: string;
}

export interface AdminReservationPage {
  content: AdminReservationView[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ReservationFilters {
  status: ReservationStatus | '';
  from: string;
  to: string;
}

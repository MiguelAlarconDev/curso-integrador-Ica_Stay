export interface CreateReservationRequest { roomId: string; checkIn: string; checkOut: string; guests: number; }
export interface ReservationView extends CreateReservationRequest {
  id: string; guestUserId: string; status: string; expiresAt: string | null;
  nightlyPriceSnapshot: number; totalAmount: number; currency: string;
}

export type ReservationStatus = 'PENDING_PAYMENT' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED';
export interface UserReservationView extends Omit<ReservationView, 'guestUserId' | 'status'> {
  hotelId: string; hotelName: string; roomNumber: string; status: ReservationStatus;
  createdAt: string; evaluatedAt: string; holdExpired: boolean;
}
export interface UserReservationPage {
  content: UserReservationView[]; page: number; size: number; totalElements: number; totalPages: number;
}

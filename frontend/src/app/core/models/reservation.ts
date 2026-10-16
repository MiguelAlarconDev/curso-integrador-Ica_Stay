export interface CreateReservationRequest { roomId: string; checkIn: string; checkOut: string; guests: number; }
export interface ReservationView extends CreateReservationRequest {
  id: string; guestUserId: string; status: string; expiresAt: string | null;
  nightlyPriceSnapshot: number; totalAmount: number; currency: string;
}

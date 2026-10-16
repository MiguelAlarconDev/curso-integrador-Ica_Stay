export interface RoomView {
  id: string;
  number: string;
  description: string | null;
  capacity: number;
  pricePerNight: number;
  currency: string;
}
export interface AvailabilityQuery { checkIn: string; checkOut: string; guests: number; }

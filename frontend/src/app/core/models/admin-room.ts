/** Matches the backend AdminRoomView; descriptions may be null. */
export interface AdminRoomView {
  id: string;
  number: string;
  description: string | null;
  capacity: number;
  pricePerNight: number;
  currency: string;
  status: 'ACTIVE' | 'INACTIVE';
}

export interface SaveRoomRequest {
  number: string;
  description: string | null;
  capacity: number;
  pricePerNight: number;
  currency: string;
}

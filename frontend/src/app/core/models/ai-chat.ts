export interface AiChatResponse { answer: string; catalog?: AiCatalog; }

export interface AiCatalog {
  checkedAt: string;
  checkIn: string | null;
  checkOut: string | null;
  guests: number | null;
  roomsRequested: boolean;
  hotels: AiHotelCard[];
}
export interface AiHotelCard {
  id: string;
  name: string;
  city: string;
  availability: 'UNKNOWN' | 'AVAILABLE' | 'UNAVAILABLE';
  rooms: { number: string; capacity: number; pricePerNight: string; currency: string }[];
}

export interface AiExchange {
  question: string;
  answer: string;
  createdAt: string;
  expiresAt: string;
}

export interface AiConversationPage {
  conversationId: string | null;
  content: AiExchange[];
  page: number;
  size: number;
  hasNext: boolean;
}

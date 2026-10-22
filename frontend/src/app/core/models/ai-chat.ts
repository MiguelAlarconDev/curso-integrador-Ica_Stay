export interface AiChatResponse { answer: string; }

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

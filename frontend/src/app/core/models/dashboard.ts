export interface DashboardView {
  hotels: number;
  rooms: number;
  users: number;
  reservations: {
    total: number;
    pendingPayment: number;
    pendingCurrent: number;
    pendingExpired: number;
    confirmed: number;
    cancelled: number;
    expired: number;
  };
  paymentAttempts: Record<string, number>;
  generatedAt: string;
}

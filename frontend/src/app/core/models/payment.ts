export type PaymentResult = 'APPROVED' | 'REJECTED';

export interface SimulatePaymentRequest { reservationId: string; result: PaymentResult; }
export interface PaymentView { id: string; result: PaymentResult; reference: string; }

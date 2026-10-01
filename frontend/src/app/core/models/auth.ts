export type AuthRole = 'USER' | 'HOTEL_ADMIN' | 'SUPER_ADMIN';

export interface LoginRequest { email: string; password: string; }
export interface LoginResponse { token: string; tokenType: 'Bearer'; }
export interface JwtClaims {
  sub: string;
  userId: string;
  role: AuthRole;
  iat: number;
  exp: number;
}
export interface AuthSession {
  readonly token: string;
  readonly email: string;
  readonly userId: string;
  readonly role: AuthRole;
  readonly issuedAt: number;
  readonly expiresAt: number;
}

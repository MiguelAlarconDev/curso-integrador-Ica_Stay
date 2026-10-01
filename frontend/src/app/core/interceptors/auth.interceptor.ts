import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { API_BASE_URL } from '../api.config';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const base = new URL(inject(API_BASE_URL));
  const url = new URL(request.url, globalThis.location.origin);
  const apiPath = `${base.pathname.replace(/\/$/, '')}/api/`;
  if (url.origin !== base.origin || !url.pathname.startsWith(apiPath)
    || url.pathname.replace(/\/$/, '') === `${apiPath}v1/auth/login`) return next(request);
  const auth = inject(AuthService);
  const token = auth.token();
  if (!token) return next(request);
  return next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && auth.token() === token) auth.logout();
      return throwError(() => error);
    }),
  );
};

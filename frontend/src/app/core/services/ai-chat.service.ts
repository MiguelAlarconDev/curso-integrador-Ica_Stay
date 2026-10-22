import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { catchError, map, throwError, timeout, TimeoutError } from 'rxjs';
import { API_BASE_URL } from '../api.config';
import { AiChatResponse } from '../models/ai-chat';

@Injectable({ providedIn: 'root' })
export class AiChatService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  chat(message: string) {
    return this.http.post<AiChatResponse>(`${this.baseUrl.replace(/\/$/, '')}/api/v1/ai/chat`, { message }).pipe(
      timeout(70_000),
      map(response => {
        if (typeof response?.answer !== 'string' || !response.answer.trim()) throw new Error('Invalid response');
        return response.answer.trim();
      }),
      catchError((error: unknown) => {
        const status = error instanceof HttpErrorResponse ? error.status : null;
        const message = error instanceof TimeoutError || status === 504
          ? 'El asistente tardó demasiado en responder. Puedes reintentar.'
          : status === 0 ? 'No pudimos conectar con IcaStay. Revisa tu conexión y reintenta.'
          : status === 503 || status === 429 ? 'El asistente está ocupado o temporalmente no disponible. Reintenta en unos momentos.'
          : 'No pudimos obtener una respuesta. Puedes reintentar en unos momentos.';
        return throwError(() => new Error(message));
      }),
    );
  }
}

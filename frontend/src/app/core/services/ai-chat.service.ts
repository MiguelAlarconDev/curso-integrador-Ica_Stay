import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { catchError, map, throwError, timeout, TimeoutError } from 'rxjs';
import { API_BASE_URL } from '../api.config';
import { AiChatResponse, AiConversationPage } from '../models/ai-chat';

@Injectable({ providedIn: 'root' })
export class AiChatService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  currentConversation() {
    return this.http.get<AiConversationPage>(`${this.baseUrl.replace(/\/$/, '')}/api/v1/ai/conversations/current`, {
      withCredentials: true, params: { page: 0, size: 50 },
    }).pipe(
      timeout(10_000),
      map(response => {
        if (!response || (response.conversationId !== null && typeof response.conversationId !== 'string')
          || response.page !== 0 || response.size !== 50 || typeof response.hasNext !== 'boolean'
          || !Array.isArray(response.content) || response.content.length > 50
          || (response.conversationId === null && (response.content.length > 0 || response.hasNext))
          || response.content.some(item => !item || typeof item.question !== 'string' || !item.question.trim()
            || item.question.length > 1000 || typeof item.answer !== 'string' || !item.answer.trim()
            || item.answer.length > 16000 || typeof item.createdAt !== 'string' || !Number.isFinite(Date.parse(item.createdAt))
            || typeof item.expiresAt !== 'string' || !Number.isFinite(Date.parse(item.expiresAt)))) {
          throw new Error('Invalid history');
        }
        // Backend order is createdAt DESC/id DESC. Reverse preserves its tie ordering.
        return { ...response, content: [...response.content].reverse() };
      }),
      catchError(() => throwError(() => new Error(
        'No pudimos recuperar la conversación. Puedes reintentar o seguir preguntando; el historial puede no estar disponible.',
      ))),
    );
  }

  chat(message: string) {
    return this.http.post<AiChatResponse>(`${this.baseUrl.replace(/\/$/, '')}/api/v1/ai/chat`, { message }, { withCredentials: true }).pipe(
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

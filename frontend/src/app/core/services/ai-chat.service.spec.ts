import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { vi } from 'vitest';
import { AiChatService } from './ai-chat.service';
import { API_BASE_URL } from '../api.config';
import { authInterceptor } from '../interceptors/auth.interceptor';
import { AuthService } from './auth.service';

describe('AiChatService public HTTP integration', () => {
  const base = 'https://api.example.test/core';
  const url = `${base}/api/v1/ai/chat`;
  const auth = { token: () => 'test-session', logout: vi.fn() };
  beforeEach(() => {
    auth.logout.mockClear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(), { provide: API_BASE_URL, useValue: `${base}/` }, { provide: AuthService, useValue: auth }] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); vi.useRealTimers(); });

  it('omits JWT even with a session and does not log out on chat 401', () => {
    TestBed.inject(AiChatService).chat('Hola').subscribe({ error: () => {} });
    const request = TestBed.inject(HttpTestingController).expectOne(url);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({}, { status: 401, statusText: 'Unauthorized' }); expect(auth.logout).not.toHaveBeenCalled();
  });

  it.each([['GET', url], ['POST', `${url}/other`], ['POST', `${base}/api/v1/reservations`]])('preserves authentication for %s %s', (method, path) => {
    TestBed.inject(HttpClient).request(method, path).subscribe();
    const request = TestBed.inject(HttpTestingController).expectOne(path);
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-session'); request.flush({});
  });

  it('cancels on client timeout and provides a safe error', () => {
    vi.useFakeTimers(); let message = '';
    TestBed.inject(AiChatService).chat('Hola').subscribe({ error: (error: Error) => message = error.message });
    const request = TestBed.inject(HttpTestingController).expectOne(url);
    vi.advanceTimersByTime(70_001);
    expect(request.cancelled).toBe(true); expect(message).toContain('tardó demasiado');
  });

  it.each([null, {}, { answer: '' }, { answer: '  ' }, { answer: 123 }])('rejects malformed success %s', body => {
    let message = '';
    TestBed.inject(AiChatService).chat('Hola').subscribe({ error: (error: Error) => message = error.message });
    TestBed.inject(HttpTestingController).expectOne(url).flush(body);
    expect(message).toContain('No pudimos obtener');
  });
});

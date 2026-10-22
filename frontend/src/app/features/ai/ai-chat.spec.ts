import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AiChat } from './ai-chat';
import { API_BASE_URL } from '../../core/api.config';
import { authInterceptor } from '../../core/interceptors/auth.interceptor';

describe('AiChat', () => {
  const api = 'https://api.example.test/api/v1/ai/chat';
  beforeEach(() => TestBed.configureTestingModule({ imports: [AiChat], providers: [
    provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(),
    { provide: API_BASE_URL, useValue: 'https://api.example.test' },
  ] }));
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  function setup() {
    const fixture = TestBed.createComponent(AiChat); fixture.detectChanges();
    const page: HTMLElement = fixture.nativeElement;
    const http = TestBed.inject(HttpTestingController);
    const toggle = () => { page.querySelector<HTMLButtonElement>('.chat-launcher')!.click(); fixture.detectChanges(); };
    const fill = (value: string) => {
      const input = page.querySelector('textarea')!;
      input.value = value; input.dispatchEvent(new Event('input')); fixture.detectChanges();
    };
    const submit = () => { page.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); fixture.detectChanges(); };
    return { fixture, page, http, toggle, fill, submit };
  }

  it('opens, welcomes, closes with Escape and restores focus without announcing history again', () => {
    const { page, fixture, toggle } = setup();
    expect(page.querySelector<HTMLElement>('.chat-panel')!.hidden).toBe(true);
    toggle();
    expect(page.querySelector<HTMLElement>('.chat-panel')!.hidden).toBe(false);
    expect(page.querySelector('.assistant-message')!.textContent).toContain('¡Hola! Soy IcaStay AI');
    expect(document.activeElement).toBe(page.querySelector('textarea'));
    page.querySelector('textarea')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })); fixture.detectChanges();
    expect(page.querySelector<HTMLElement>('.chat-panel')!.hidden).toBe(true);
    expect(document.activeElement).toBe(page.querySelector('.chat-launcher'));
    toggle(); expect(page.querySelectorAll('.assistant-message').length).toBe(1);
    expect(page.querySelector('[aria-live="polite"]')!.textContent).toBe('');
  });

  it('posts anonymously, renders text safely and blocks duplicate sends while loading', () => {
    const { page, fixture, toggle, fill, submit, http } = setup(); toggle(); fill('  Hola Ica  '); submit(); submit();
    const request = http.expectOne(api);
    expect(request.request.body).toEqual({ message: 'Hola Ica' });
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.has('Authorization')).toBe(false);
    expect(page.querySelector('.chat-loading')).not.toBeNull();
    expect(page.querySelector<HTMLButtonElement>('.send-chat')!.disabled).toBe(true);
    expect(page.querySelectorAll('.user-message').length).toBe(1);
    request.flush({ answer: '<b>Consulta el catálogo</b>' }); fixture.detectChanges();
    expect(page.querySelectorAll('.assistant-message').length).toBe(2);
    expect(page.querySelector('.chat-history b')).toBeNull();
    expect(page.querySelector('.chat-history')!.textContent).toContain('<b>Consulta el catálogo</b>');
    expect(page.querySelector('.chat-loading')).toBeNull();
    expect(page.querySelector<HTMLButtonElement>('.send-chat')!.disabled).toBe(false);
    expect(page.querySelectorAll('[aria-live="polite"]').length).toBe(1);
    expect(page.querySelector('[aria-live="polite"]')!.textContent).toContain('Consulta el catálogo');
  });

  it.each(['', '  \n\t ', 'a'.repeat(1001)])('rejects invalid input (%s)', value => {
    const { toggle, fill, submit, http, page } = setup(); toggle(); fill(value); submit();
    http.expectNone(api); expect(page.querySelector('.validation')!.textContent).toContain('1000');
    expect(page.querySelectorAll('.user-message').length).toBe(0);
  });

  it('accepts exactly 1000 characters and Enter, but not Shift+Enter or IME Enter', () => {
    const { toggle, fill, http, page, fixture } = setup(); toggle(); fill('a'.repeat(1000));
    const input = page.querySelector('textarea')!;
    for (const options of [{ shiftKey: true }, { isComposing: true }]) {
      const event = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true, ...options });
      input.dispatchEvent(event); expect(event.defaultPrevented).toBe(false); http.expectNone(api);
    }
    const event = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
    input.dispatchEvent(event); fixture.detectChanges(); expect(event.defaultPrevented).toBe(true);
    http.expectOne(api).flush({ answer: 'Respuesta' }); fixture.detectChanges();
  });

  it.each([0, 503, 504, 500])('handles error %s and retries without duplicating the question', status => {
    const { toggle, fill, submit, http, page, fixture } = setup(); toggle(); fill('Pregunta'); submit();
    const request = http.expectOne(api);
    if (status === 0) request.error(new ProgressEvent('error'));
    else request.flush({ secret: 'internal-details' }, { status, statusText: 'Failure' });
    fixture.detectChanges(); expect(page.querySelector('.message-error')).not.toBeNull();
    expect(page.textContent).not.toContain('internal-details');
    page.querySelector<HTMLButtonElement>('.retry-chat')!.click(); fixture.detectChanges();
    expect(page.querySelectorAll('.user-message').length).toBe(1);
    const retry = http.expectOne(api); expect(retry.request.body).toEqual({ message: 'Pregunta' });
    retry.flush({ answer: 'Respuesta' }); fixture.detectChanges();
    expect(page.querySelectorAll('.user-message').length).toBe(1);
    expect(page.querySelectorAll('.assistant-message').length).toBe(2);
    expect(page.querySelector('.message-error')).toBeNull();
  });

  it('keeps messages on close and cancels pending requests on destruction', () => {
    const { toggle, fill, submit, http, page, fixture } = setup(); toggle(); fill('Consulta'); submit();
    const request = http.expectOne(api); toggle(); expect(request.cancelled).toBe(false);
    toggle(); expect(page.querySelectorAll('.user-message').length).toBe(1);
    fixture.destroy(); expect(request.cancelled).toBe(true);
    expect(() => request.flush({ answer: 'late' })).toThrow();
  });
});

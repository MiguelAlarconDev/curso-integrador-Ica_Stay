import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';
import { Register } from './register';
import { API_BASE_URL } from '../../../core/api.config';
import { AuthService } from '../../../core/services/auth.service';
import { authInterceptor } from '../../../core/interceptors/auth.interceptor';
import { routes } from '../../../app.routes';

describe('Register', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ imports: [Register], providers: [provideRouter(routes),
      provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });

  function setup() {
    const fixture = TestBed.createComponent(Register); fixture.detectChanges();
    const page: HTMLElement = fixture.nativeElement;
    const fill = (id: string, value: string) => {
      const input = page.querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = value; input.dispatchEvent(new Event('input')); fixture.detectChanges();
    };
    const submit = () => {
      page.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      fixture.detectChanges();
    };
    const valid = () => {
      fill('name', '  Usuario de Prueba  '); fill('email', 'demo@example.test');
      fill('password', 'TestPassword123!'); fill('confirmation', 'TestPassword123!');
    };
    return { fixture, page, fill, submit, valid };
  }

  it('makes /register public and provides an existing-account link', async () => {
    const harness = await RouterTestingHarness.create('/register');
    expect(harness.routeNativeElement!.querySelector('form')).not.toBeNull();
    expect(harness.routeNativeElement!.textContent).toContain('Ya tengo una cuenta');
    expect(TestBed.inject(AuthService).session()).toBeNull();
  });

  it('shows required messages without sending an empty form', () => {
    const { page, submit } = setup(); submit();
    expect(page.querySelector('#name-error')!.textContent).toContain('nombre completo');
    expect(page.querySelector('#email-error')!.textContent).toContain('correo válido');
    expect(page.querySelector('#password-error')!.textContent).toContain('8 caracteres');
    expect(page.querySelector('#confirmation-error')!.textContent).toContain('coincidir');
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it.each([
    ['name', '   '], ['name', 'a'.repeat(161)], ['email', 'invalid'],
    ['email', 'user@localhost'], ['email', 'a'.repeat(243) + '@example.test'],
    ['password', '1234567'], ['confirmation', 'different'], ['confirmation', ''],
  ])('rejects invalid %s before HTTP', (field, value) => {
    const { valid, fill, submit } = setup(); valid(); fill(field, value); submit();
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('sends only the DTO once and redirects to login without opening a session', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const { fixture, page, valid, submit } = setup(); valid(); submit(); submit();
    const request = TestBed.inject(HttpTestingController).expectOne(`${TestBed.inject(API_BASE_URL)}/api/v1/auth/register`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Usuario de Prueba', email: 'demo@example.test', password: 'TestPassword123!' });
    expect(request.request.headers.has('Authorization')).toBe(false);
    expect(page.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(true);
    request.flush({ id: 'test-id', name: 'Usuario de Prueba', email: 'demo@example.test', role: 'USER' }, { status: 201, statusText: 'Created' });
    fixture.detectChanges();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { registered: '1' } });
    expect(TestBed.inject(AuthService).session()).toBeNull();
    expect(sessionStorage.length).toBe(0);
    expect(page.querySelector<HTMLInputElement>('#password')!.value).toBe('');
    expect(page.querySelector<HTMLInputElement>('#confirmation')!.value).toBe('');
  });

  it.each([
    [400, 'Revisa los datos ingresados'], [409, 'Este correo ya está registrado'],
    [0, 'No pudimos conectar con el servidor'], [500, 'No pudimos crear tu cuenta'],
  ])('handles HTTP %s safely and enables retry', (status, message) => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
    const { fixture, page, valid, submit } = setup(); valid(); submit();
    const request = TestBed.inject(HttpTestingController).expectOne(`${TestBed.inject(API_BASE_URL)}/api/v1/auth/register`);
    if (status === 0) request.error(new ProgressEvent('error'));
    else request.flush({ message: 'PRIVATE SERVER DETAIL' }, { status: Number(status), statusText: 'Rejected' });
    fixture.detectChanges();
    expect(page.querySelector('[role="alert"]')!.textContent).toContain(String(message));
    expect(page.textContent).not.toContain('PRIVATE SERVER DETAIL');
    expect(page.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
    expect(TestBed.inject(AuthService).session()).toBeNull();
    submit();
    TestBed.inject(HttpTestingController).expectOne(`${TestBed.inject(API_BASE_URL)}/api/v1/auth/register`)
      .flush({}, { status: 409, statusText: 'Conflict' });
  });

  it('shows the confirmation on login and keeps the create-account link', async () => {
    const harness = await RouterTestingHarness.create('/login?registered=1');
    expect(harness.routeNativeElement!.textContent).toContain('Tu cuenta se creó correctamente. Ya puedes iniciar sesión.');
    expect(harness.routeNativeElement!.querySelector('a[href="/register"]')!.textContent).toContain('Crear cuenta');
  });
  it('does not show success on an ordinary login visit', async () => {
    const harness = await RouterTestingHarness.create('/login');
    expect(harness.routeNativeElement!.querySelector('.registration-success')).toBeNull();
  });
});

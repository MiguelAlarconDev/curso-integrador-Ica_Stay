import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { Login } from './login';
import { API_BASE_URL } from '../../../core/api.config';

describe('Login', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ imports: [Login], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });
  function setup() {
    const fixture = TestBed.createComponent(Login); fixture.detectChanges();
    const page: HTMLElement = fixture.nativeElement;
    const submit = () => { page.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); fixture.detectChanges(); };
    const fill = (id: string, value: string) => {
      const input = page.querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = value; input.dispatchEvent(new Event('input')); fixture.detectChanges();
    };
    return { fixture, page, submit, fill };
  }
  it('validates missing values and invalid email without sending', () => {
    const { page, submit, fill } = setup(); submit();
    expect(page.textContent).toContain('Ingresa un correo electrónico válido');
    expect(page.textContent).toContain('Ingresa tu contraseña');
    fill('email', 'not-an-email'); fill('password', 'test-only'); submit();
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });
  it.each([401, 0, 500])('shows a safe message for status %s and prevents duplicate submits', status => {
    const { fixture, page, submit, fill } = setup();
    fill('email', 'user@example.com'); fill('password', 'wrong'); submit(); submit();
    const request = TestBed.inject(HttpTestingController).expectOne(`${TestBed.inject(API_BASE_URL)}/api/v1/auth/login`);
    expect(page.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(true);
    if (status === 0) request.error(new ProgressEvent('error'));
    else request.flush({ message: 'PRIVATE SERVER DETAIL' }, { status, statusText: 'Rejected' });
    fixture.detectChanges();
    expect(page.textContent).toContain(status === 401 ? 'El correo o la contraseña no son correctos.' : status === 0 ? 'No pudimos conectar.' : 'No pudimos iniciar sesión.');
    expect(page.textContent).not.toContain('PRIVATE SERVER DETAIL');
    expect(page.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(false);
  });
  it('navigates home after success and updates the header; logout removes it', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { fixture, page, submit, fill } = setup();
    fill('email', 'user@example.com'); fill('password', 'test-only'); submit();
    const now = Math.floor(Date.now() / 1000);
    const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
    const token = `${encode({ alg: 'HS256' })}.${encode({ sub: 'user@example.com', userId: 'test-user', role: 'USER', iat: now, exp: now + 3600 })}.c2ln`;
    TestBed.inject(HttpTestingController).expectOne(`${TestBed.inject(API_BASE_URL)}/api/v1/auth/login`).flush({ token, tokenType: 'Bearer' });
    fixture.detectChanges();
    expect(navigate).toHaveBeenCalledWith('/');
    expect(page.querySelector('header')!.textContent).toContain('user@example.com');
    page.querySelector<HTMLButtonElement>('.session button')!.click(); fixture.detectChanges();
    expect(page.querySelector('header')!.textContent).not.toContain('user@example.com');
    expect(sessionStorage.length).toBe(0);
  });
});

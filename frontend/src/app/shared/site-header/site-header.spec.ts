import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { SiteHeader } from './site-header';

describe('SiteHeader Home navigation', () => {
  const role = signal<string | null>(null);
  const session = signal<{ email: string; role: string } | null>(null);
  const logout = vi.fn(() => { role.set(null); session.set(null); });

  beforeEach(() => {
    role.set(null); session.set(null); logout.mockClear();
    TestBed.configureTestingModule({
      imports: [SiteHeader],
      providers: [provideRouter([]), { provide: AuthService, useValue: { role, session, logout } }],
    });
  });

  function setup(home = true) {
    const fixture = TestBed.createComponent(SiteHeader);
    fixture.componentRef.setInput('home', home);
    fixture.detectChanges();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  it('keeps the default header without a Home menu or theme', () => {
    const { page } = setup(false);
    expect(page.classList.contains('home-header')).toBe(false);
    expect(page.querySelector('.menu-toggle')).toBeNull();
    expect(page.querySelector('.wordmark')?.textContent).toContain('ICA STAY');
    expect(page.querySelector('a[href="/login"]')).not.toBeNull();
  });

  it('opens and closes with Escape, restoring focus and preserving anchors', () => {
    const { fixture, page } = setup();
    const toggle = page.querySelector<HTMLButtonElement>('.menu-toggle')!;
    const focus = vi.spyOn(toggle, 'focus');
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(toggle.getAttribute('aria-controls')).toBe(page.querySelector('.navigation')?.id);
    toggle.click(); fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    page.querySelector('nav a')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(focus).toHaveBeenCalled();
    expect(page.querySelector('a[href="/#alojamientos"]')).not.toBeNull();
    expect(page.querySelector('a[href="/#explorar"]')).not.toBeNull();
    expect(page.querySelector('.skip-link')?.getAttribute('href')).toBe('#contenido');
  });

  it.each([
    ['USER', '/my-reservations'],
    ['HOTEL_ADMIN', '/admin/hotel'],
    ['SUPER_ADMIN', '/superadmin/dashboard'],
  ])('preserves %s navigation and logout in the expanded menu', (userRole, destination) => {
    role.set(userRole); session.set({ email: 'guest@example.test', role: userRole });
    const { fixture, page } = setup();
    expect(page.querySelector(`a[href="${destination}"]`)).not.toBeNull();
    expect(page.querySelector('a[href="/login"]')).toBeNull();
    expect(!!page.querySelector('a[href="/my-reservations"]')).toBe(userRole === 'USER');
    expect(page.querySelectorAll('.session a').length).toBe(userRole === 'USER' ? 0 : 1);
    page.querySelector<HTMLButtonElement>('.menu-toggle')!.click(); fixture.detectChanges();
    page.querySelector<HTMLButtonElement>('.session button')!.click(); fixture.detectChanges();
    expect(logout).toHaveBeenCalledOnce();
    expect(page.querySelector('.menu-toggle')?.getAttribute('aria-expanded')).toBe('false');
    expect(page.querySelector('a[href="/login"]')).not.toBeNull();
  });

  it('closes after choosing a section link', () => {
    const { fixture, page } = setup();
    page.querySelector<HTMLButtonElement>('.menu-toggle')!.click(); fixture.detectChanges();
    // Prevent actual navigation while exercising the bubbling selection event.
    const link = page.querySelector('nav a')!;
    link.addEventListener('click', event => event.preventDefault());
    link.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, button: 1 }));
    fixture.detectChanges();
    expect(page.querySelector('.menu-toggle')?.getAttribute('aria-expanded')).toBe('false');
  });
});

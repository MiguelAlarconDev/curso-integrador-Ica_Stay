import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SuperadminNav } from './superadmin-nav';
import { ReservationState } from '../reservations/reservation-state';
import { UserReservationView } from '../../core/models/reservation';

describe('Frontend 2.0 admin UI pieces', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [SuperadminNav, ReservationState], providers: [provideRouter([])] }));

  it('lists only the implemented SUPER_ADMIN sections', () => {
    const fixture = TestBed.createComponent(SuperadminNav); fixture.detectChanges();
    const nav: HTMLElement = fixture.nativeElement.querySelector('nav');
    expect(nav.getAttribute('aria-label')).toBe('Secciones de administración');
    expect(Array.from(nav.querySelectorAll('a')).map(link => link.getAttribute('href')))
      .toEqual(['/superadmin/dashboard', '/superadmin/hotels', '/superadmin/users']);
  });

  it.each([
    [{ status: 'PENDING_PAYMENT', holdExpired: false }, 'badge-pending_payment', 'Pendiente de pago; no es una reserva confirmada.'],
    [{ status: 'PENDING_PAYMENT', holdExpired: true }, 'badge-expired', 'Retención vencida'],
    [{ status: 'CONFIRMED', holdExpired: false }, 'badge-confirmed', 'Confirmada'],
  ])('shows reservation state %j with a styled badge and real text', (state, badge, text) => {
    const fixture = TestBed.createComponent(ReservationState);
    fixture.componentRef.setInput('reservation', { ...state, evaluatedAt: '2030-01-01T00:00:00Z' } as unknown as UserReservationView);
    fixture.detectChanges();
    const page: HTMLElement = fixture.nativeElement;
    expect(page.querySelector('.badge')!.classList).toContain(badge);
    expect(page.querySelector('.badge')!.classList).toContain('badge');
    expect(page.textContent).toContain(text); expect(page.textContent).toContain(`(${state.status})`);
  });
});

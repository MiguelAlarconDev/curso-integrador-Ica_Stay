import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-admin-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './admin-layout.html', styleUrl: './admin-layout.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminLayout {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly navigation = toSignal(this.router.events.pipe(filter(event => event instanceof NavigationEnd)));
  protected readonly section = computed(() => {
    this.navigation();
    return this.route.firstChild?.snapshot.data['section'] ?? 'Resumen';
  });
  protected readonly links = [
    { path: '/admin/hotel', label: 'Resumen' },
    { path: '/admin/hotel/profile', label: 'Mi hotel' },
    { path: '/admin/hotel/rooms', label: 'Habitaciones' },
    { path: '/admin/hotel/reservations', label: 'Reservas' },
  ];

  constructor() {
    // The existing session signal also changes on expiry or an authenticated 401.
    effect(() => {
      if (!this.auth.session()) void this.router.navigateByUrl('/login');
    });
  }

  protected logout(): void { this.auth.logout(); }
}

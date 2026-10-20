import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, KeyValuePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { SiteHeader } from '../../shared/site-header/site-header';
import { AuthService } from '../../core/services/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { DashboardView } from '../../core/models/dashboard';

@Component({
  selector: 'app-superadmin-dashboard',
  imports: [SiteHeader, DatePipe, DecimalPipe, KeyValuePipe, RouterLink],
  templateUrl: './dashboard.html', styleUrl: './dashboard.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard {
  private readonly api = inject(DashboardService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly data = signal<DashboardView | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly empty = computed(() => {
    const d = this.data();
    return d !== null && d.hotels === 0 && d.rooms === 0 && d.users === 0
      && d.reservations.total === 0 && Object.values(d.paymentAttempts).every(count => count === 0);
  });
  protected readonly results: Record<string, string> = { APPROVED: 'Aprobados', REJECTED: 'Rechazados' };

  constructor() {
    effect(() => {
      if (!this.auth.session()) {
        this.data.set(null);
        void this.router.navigateByUrl('/login');
      }
    });
    this.load();
  }

  protected load(): void {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    this.data.set(null);
    this.api.get().pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: data => this.data.set(data),
        error: (error: unknown) => this.error.set(error instanceof HttpErrorResponse && error.status === 403
          ? 'No tienes permiso para consultar estas estadísticas.'
          : 'No pudimos cargar las estadísticas. Inténtalo de nuevo.'),
      });
  }
}

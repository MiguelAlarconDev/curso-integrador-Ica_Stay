import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, ValidatorFn } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, Subscription } from 'rxjs';
import { AdminReservationService } from '../../../../core/services/admin-reservation.service';
import { AdminReservationPage, AdminReservationView, ReservationFilters, ReservationStatus } from '../../../../core/models/admin-reservation';

const emptyFilters: ReservationFilters = { status: '', from: '', to: '' };
const dateRange: ValidatorFn = control => {
  const { from, to } = control.value;
  const valid = (date: string) => !date || (/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(date)
    && date >= '0001-01-01' && Number.isFinite(Date.parse(date)) && new Date(date).toISOString().slice(0, 10) === date);
  return valid(from) && valid(to) && (!from || !to || from <= to) ? null : { dates: true };
};

@Component({
  selector: 'app-hotel-reservations', imports: [ReactiveFormsModule, DatePipe, DecimalPipe],
  templateUrl: './reservations.html', styleUrl: './reservations.css', changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HotelReservations implements OnInit {
  private readonly service = inject(AdminReservationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly listRegion = viewChild.required<ElementRef<HTMLElement>>('listRegion');
  private listRequest?: Subscription;
  private detailRequest?: Subscription;
  private applied: ReservationFilters = { ...emptyFilters };
  private requestedPage = 0;
  protected readonly result = signal<AdminReservationPage | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly selectedId = signal<string | null>(null);
  protected readonly detail = signal<AdminReservationView | null>(null);
  protected readonly detailLoading = signal(false);
  protected readonly detailError = signal('');
  protected readonly labels: Record<ReservationStatus, string> = {
    PENDING_PAYMENT: 'Pendiente de pago', CONFIRMED: 'Confirmada', CANCELLED: 'Cancelada', EXPIRED: 'Expirada',
  };
  protected readonly statuses = Object.keys(this.labels) as ReservationStatus[];
  protected readonly form = new FormGroup({
    status: new FormControl<ReservationStatus | ''>('', { nonNullable: true }),
    from: new FormControl('', { nonNullable: true }), to: new FormControl('', { nonNullable: true }),
  }, { validators: dateRange });

  ngOnInit(): void { this.load(0); }

  protected apply(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const filters = this.form.getRawValue();
    if (JSON.stringify(filters) === JSON.stringify(this.applied) && this.requestedPage === 0 && !this.error()) return;
    this.applied = filters; this.load(0);
  }

  protected clear(): void { this.form.reset(emptyFilters); this.apply(); }
  protected retry(): void { if (!this.loading()) this.load(this.requestedPage); }
  protected page(page: number): void {
    if (this.loading() || page < 0 || page >= (this.result()?.totalPages ?? 0)) return;
    this.load(page);
  }

  private load(page: number): void {
    this.listRequest?.unsubscribe(); this.closeDetail();
    this.requestedPage = page; this.loading.set(true); this.error.set('');
    this.listRequest = this.service.list(page, this.applied)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({ next: result => this.result.set(result), error: error => this.error.set(this.message(error, false)) });
  }

  protected showDetail(id: string): void {
    if (id === this.selectedId() && this.detailLoading()) return;
    this.detailRequest?.unsubscribe();
    this.selectedId.set(id); this.detail.set(null); this.detailError.set(''); this.detailLoading.set(true);
    this.detailRequest = this.service.get(id)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.detailLoading.set(false)))
      .subscribe({ next: detail => this.detail.set(detail), error: error => this.detailError.set(this.message(error, true)) });
  }
  protected closeDetail(focusList = false): void {
    this.detailRequest?.unsubscribe(); this.selectedId.set(null); this.detail.set(null); this.detailError.set('');
    if (focusList) this.listRegion().nativeElement.focus();
  }

  private message(error: unknown, detail: boolean): string {
    switch (error instanceof HttpErrorResponse ? error.status : -1) {
      case 0: return 'No pudimos conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.';
      case 400: return 'El servidor no pudo validar los filtros. Revisa el estado y el intervalo de fechas.';
      case 401: return 'Tu sesión ya no es válida. Inicia sesión de nuevo.';
      case 403: return 'No tienes permiso para consultar estas reservas.';
      case 404: return detail ? 'La reserva no existe o no pertenece a tu hotel.' : 'No tienes un hotel asociado. Contacta con la administración.';
      default: return 'No pudimos cargar ' + (detail ? 'la reserva.' : 'las reservas.') + ' Inténtalo de nuevo más tarde.';
    }
  }
}

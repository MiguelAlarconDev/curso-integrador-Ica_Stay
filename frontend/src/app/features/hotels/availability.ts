import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, ValidatorFn } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, Subscription } from 'rxjs';
import { RoomService } from '../../core/services/room.service';
import { RoomView } from '../../core/models/room';

const validQuery: ValidatorFn = control => {
  const { checkIn, checkOut, guests } = control.value;
  const date = (s: string) => /^\d{4}-\d{2}-\d{2}$/.test(s) && Number.isFinite(Date.parse(s)) && new Date(s).toISOString().slice(0, 10) === s;
  return date(checkIn) && date(checkOut) && checkIn >= new Date().toISOString().slice(0, 10) && checkOut > checkIn
    && Number.isInteger(guests) && guests > 0 && guests <= 2147483647 ? null : { availability: true };
};
@Component({
  selector: 'app-availability', imports: [ReactiveFormsModule, DecimalPipe],
  templateUrl: './availability.html', styleUrl: './hotels.css', changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Availability {
  readonly hotelId = input.required<string>();
  private readonly service = inject(RoomService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly rooms = signal<RoomView[] | null>(null);
  protected readonly form = new FormGroup({
    checkIn: new FormControl('', { nonNullable: true }), checkOut: new FormControl('', { nonNullable: true }),
    guests: new FormControl(1, { nonNullable: true }),
  }, { validators: validQuery });
  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.request?.unsubscribe(); this.rooms.set(null); this.error.set('');
    });
  }
  protected search(): void {
    this.form.updateValueAndValidity({ emitEvent: false }); this.form.markAllAsTouched();
    if (this.loading() || this.form.invalid) return;
    this.request?.unsubscribe(); this.loading.set(true); this.error.set(''); this.rooms.set(null);
    this.request = this.service.available(this.hotelId(), this.form.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({ next: rooms => this.rooms.set(rooms), error: error => this.error.set(error.status === 404
        ? 'El hotel ya no está disponible.' : error.status === 400 ? 'Revisa las fechas y los huéspedes (fecha actual en UTC).'
        : error.status === 0 ? 'No pudimos conectar con el servidor.' : 'No pudimos consultar disponibilidad. Inténtalo de nuevo.') });
  }
}

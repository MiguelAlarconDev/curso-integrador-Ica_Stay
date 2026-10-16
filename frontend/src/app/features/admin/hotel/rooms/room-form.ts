import { AfterViewInit, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, input, OnInit, output, signal, viewChild } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AdminRoomView, SaveRoomRequest } from '../../../../core/models/admin-room';
import { AdminRoomService } from '../../../../core/services/admin-room.service';
import { roomMutationError } from './room-mutation-error';

const numberValidator: ValidatorFn = control => {
  const value: string = control.value;
  // Java String.strip whitespace (NBSP is intentionally not stripped).
  return !value || value.length > 40 || /^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]$/.test(value) ? { number: true } : null;
};
const capacityValidator: ValidatorFn = control => Number.isInteger(control.value) && control.value > 0 && control.value <= 2147483647 ? null : { capacity: true };
const priceValidator: ValidatorFn = control => {
  const value = String(control.value ?? '');
  return /^\d+(\.\d{1,2})?$/.test(value) && Number(value) > 0 && Number(value) <= 9999999999.99 ? null : { price: true };
};

@Component({
  selector: 'app-room-form', imports: [ReactiveFormsModule], templateUrl: './room-form.html',
  styleUrl: './room-form.css', changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RoomForm implements OnInit, AfterViewInit {
  readonly room = input<AdminRoomView | null>(null);
  readonly saved = output<AdminRoomView>();
  readonly cancelled = output<void>();
  readonly busy = output<boolean>();
  private readonly service = inject(AdminRoomService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly firstField = viewChild.required<ElementRef<HTMLInputElement>>('firstField');
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly form = new FormGroup({
    number: new FormControl('', { nonNullable: true, validators: numberValidator }),
    description: new FormControl('', { nonNullable: true }),
    capacity: new FormControl<number | null>(1, capacityValidator),
    pricePerNight: new FormControl('', { nonNullable: true, validators: priceValidator }),
    currency: new FormControl('PEN', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^[A-Z]{3}$/)] }),
  });
  ngOnInit(): void {
    const room = this.room();
    if (room) this.form.reset({ ...room, description: room.description ?? '', pricePerNight: String(room.pricePerNight) });
  }
  ngAfterViewInit(): void { this.firstField().nativeElement.focus(); }
  protected invalid(field: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || control.dirty);
  }
  protected cancel(): void { if (!this.saving()) this.cancelled.emit(); }
  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.saving() || this.form.invalid) return;
    const value = this.form.getRawValue();
    const payload: SaveRoomRequest = { ...value, description: value.description || null,
      capacity: value.capacity!, pricePerNight: Number(value.pricePerNight) };
    const room = this.room();
    this.saving.set(true); this.busy.emit(true); this.error.set('');
    (room ? this.service.update(room.id, payload) : this.service.create(payload))
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => { this.saving.set(false); this.busy.emit(false); }))
      .subscribe({ next: result => this.saved.emit(result), error: error => this.error.set(roomMutationError(error)) });
  }
}

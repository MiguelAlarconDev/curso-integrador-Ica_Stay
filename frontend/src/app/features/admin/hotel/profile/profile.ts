import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, ValidatorFn } from '@angular/forms';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AdminHotelService } from '../../../../core/services/admin-hotel.service';
import { HotelView, UpdateHotelRequest } from '../../../../core/models/hotel';

// Match Java String.strip / Character.isWhitespace, including its NBSP exclusions.
function strip(value: string): string {
  return value.replace(/^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/g, '');
}
function textValidator(max: number, required = true): ValidatorFn {
  return control => {
    const value = strip(control.value ?? '');
    return required && !value ? { required: true } : value.length > max ? { maxlength: true } : null;
  };
}

@Component({
  selector: 'app-hotel-profile', imports: [ReactiveFormsModule],
  templateUrl: './profile.html', styleUrl: './profile.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HotelProfile implements OnInit {
  private readonly service = inject(AdminHotelService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly hotel = signal<HotelView | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly loadError = signal('');
  protected readonly saveError = signal('');
  protected readonly success = signal('');
  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: textValidator(180) }),
    description: new FormControl('', { nonNullable: true, validators: textValidator(5000, false) }),
    address: new FormControl('', { nonNullable: true, validators: textValidator(255) }),
    city: new FormControl('', { nonNullable: true, validators: textValidator(120) }),
  });
  private readonly values = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  private readonly status = toSignal(this.form.statusChanges, { initialValue: this.form.status });
  protected readonly changed = computed(() => {
    this.values();
    const hotel = this.hotel();
    return hotel !== null && JSON.stringify(this.payload()) !== JSON.stringify(this.normalize(hotel));
  });
  protected readonly canSave = computed(() => this.changed() && this.status() === 'VALID' && !this.saving());

  ngOnInit(): void { this.load(); }

  protected load(): void {
    if (this.loading() || this.saving()) return;
    this.loading.set(true); this.loadError.set(''); this.success.set('');
    this.service.get().pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: hotel => { this.hotel.set(hotel); this.cancel(); },
        error: error => this.loadError.set(this.message(error, false)),
      });
  }

  protected cancel(): void {
    if (this.saving()) return;
    const hotel = this.hotel();
    if (hotel) this.form.reset({ name: hotel.name, description: hotel.description ?? '', address: hotel.address, city: hotel.city });
    this.saveError.set(''); this.success.set('');
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (!this.canSave()) return;
    this.saving.set(true); this.saveError.set(''); this.success.set('');
    this.service.update(this.payload()).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving.set(false)))
      .subscribe({
        next: hotel => {
          this.hotel.set(hotel);
          this.form.reset({ name: hotel.name, description: hotel.description ?? '', address: hotel.address, city: hotel.city });
          this.success.set('Los cambios de tu hotel se guardaron correctamente.');
        },
        error: error => this.saveError.set(this.message(error, true)),
      });
  }

  protected invalid(field: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || control.dirty);
  }

  private payload(): UpdateHotelRequest { return this.normalize(this.form.getRawValue()); }

  private normalize(value: { name: string; description: string | null; address: string; city: string }): UpdateHotelRequest {
    return { name: strip(value.name), description: strip(value.description ?? '') || null,
      address: strip(value.address), city: strip(value.city) };
  }

  private message(error: unknown, saving: boolean): string {
    const status = error instanceof HttpErrorResponse ? error.status : -1;
    switch (status) {
      case 0: return 'No pudimos conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.';
      case 400: return 'Revisa los datos del formulario. El servidor no pudo validar la solicitud.';
      case 401: return 'Tu sesión ya no es válida. Inicia sesión de nuevo.';
      case 403: return 'No tienes permiso para acceder a la gestión de este hotel.';
      case 404: return 'No tienes un hotel asociado. Contacta con la administración de Ica Stay.';
      case 409: return 'No se pudieron guardar los cambios por un conflicto. Revisa los datos e inténtalo de nuevo.';
      default: return saving ? 'No pudimos guardar los cambios. Tus ediciones se conservan; inténtalo de nuevo.'
        : 'No pudimos cargar tu hotel. Inténtalo de nuevo más tarde.';
    }
  }
}

import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { SiteHeader } from '../../shared/site-header/site-header';
import { AuthService } from '../../core/services/auth.service';
import { SuperadminHotelService } from '../../core/services/superadmin-hotel.service';

const required = (max: number) => [Validators.required, Validators.maxLength(max), Validators.pattern(/\S/)];
function passwordBytes(control: AbstractControl) {
  return new TextEncoder().encode(control.value ?? '').length > 72 ? { passwordBytes: true } : null;
}

@Component({
  selector: 'app-create-hotel', imports: [SiteHeader, RouterLink, ReactiveFormsModule],
  templateUrl: './create-hotel.html', styleUrl: './create-hotel.css', changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateHotel {
  private readonly api = inject(SuperadminHotelService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly sending = signal(false);
  protected readonly error = signal('');
  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: required(180) }),
    description: new FormControl('', { nonNullable: true, validators: Validators.maxLength(5000) }),
    address: new FormControl('', { nonNullable: true, validators: required(255) }),
    city: new FormControl('', { nonNullable: true, validators: required(120) }),
    adminName: new FormControl('', { nonNullable: true, validators: required(160) }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(254), Validators.pattern(/^[^@\s]+@[^@\s]+\.[^@\s]+$/)] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(8), passwordBytes] }),
    confirmation: new FormControl('', { nonNullable: true, validators: Validators.required }),
  }, { validators: control => control.get('password')?.value === control.get('confirmation')?.value ? null : { mismatch: true } });
  protected readonly groups = [
    { label: 'Datos del hotel', fields: [
      { key: 'name', label: 'Nombre del hotel', type: 'text', max: 180, help: 'Obligatorio, hasta 180 caracteres.' },
      { key: 'address', label: 'Dirección', type: 'text', max: 255, help: 'Obligatoria, hasta 255 caracteres.' },
      { key: 'city', label: 'Ciudad', type: 'text', max: 120, help: 'Obligatoria, hasta 120 caracteres.' },
      { key: 'description', label: 'Descripción (opcional)', type: 'textarea', max: 5000, help: 'Hasta 5000 caracteres.' },
    ] },
    { label: 'Nuevo administrador', fields: [
      { key: 'adminName', label: 'Nombre completo', type: 'text', max: 160, help: 'Obligatorio, hasta 160 caracteres.' },
      { key: 'email', label: 'Correo electrónico', type: 'email', max: 254, help: 'Correo válido que no esté registrado, hasta 254 caracteres.' },
      { key: 'password', label: 'Contraseña inicial', type: 'password', max: 72, help: 'Mínimo 8 caracteres. Máximo 72 bytes: algunos símbolos y letras ocupan más de uno.' },
      { key: 'confirmation', label: 'Confirmar contraseña', type: 'password', max: 72, help: 'Debe coincidir con la contraseña inicial.' },
    ] },
  ] as const;

  constructor() {
    effect(() => {
      if (!this.auth.session()) { this.form.reset(); void this.router.navigateByUrl('/login'); }
    });
    this.destroyRef.onDestroy(() => this.form.reset());
  }

  protected invalid(key: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[key];
    return control.touched && (control.invalid || (key === 'confirmation' && this.form.hasError('mismatch')));
  }

  protected submit(): void {
    if (this.sending()) return;
    for (const key of ['name', 'description', 'address', 'city', 'adminName', 'email'] as const) {
      const control = this.form.controls[key]; control.setValue(control.value.trim());
    }
    this.form.markAllAsTouched(); this.error.set('');
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    this.sending.set(true);
    this.api.create({ name: value.name, description: value.description, address: value.address, city: value.city,
      admin: { name: value.adminName, email: value.email.toLowerCase(), password: value.password },
    }).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.sending.set(false))).subscribe({
      next: () => {
        this.form.reset();
        void this.router.navigate(['/superadmin/hotels'], { queryParams: { created: '1' } });
      },
      error: (error: unknown) => {
        this.form.controls.password.reset(); this.form.controls.confirmation.reset();
        const status = error instanceof HttpErrorResponse ? error.status : -1;
        this.error.set(status === 409 ? 'El correo del administrador ya está registrado. Usa otro correo.'
          : status === 400 ? 'Revisa los datos del hotel y del administrador.'
          : status === 403 ? 'No tienes permiso para registrar hoteles.'
          : status === 0 ? 'No pudimos confirmar la creación. Revisa el listado antes de volver a enviar.'
          : 'No pudimos crear el hotel. Inténtalo de nuevo más tarde.');
      },
    });
  }
}

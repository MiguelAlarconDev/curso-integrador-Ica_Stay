import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { SiteHeader } from '../../../shared/site-header/site-header';

function matchingPasswords(control: AbstractControl): ValidationErrors | null {
  return control.get('password')?.value === control.get('confirmation')?.value ? null : { passwordMismatch: true };
}

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, SiteHeader],
  templateUrl: './register.html',
  // Registration and login share the approved editorial auth layout.
  styleUrl: '../login/login.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly sending = signal(false);
  protected readonly error = signal('');
  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(160)] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email, Validators.maxLength(254), Validators.pattern(/^[^@\s]+@[^@\s]+\.[^@\s]+$/)] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(8)] }),
    confirmation: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  }, { validators: matchingPasswords });

  protected submit(): void {
    if (this.sending()) return;
    this.form.controls.name.setValue(this.form.controls.name.value.trim());
    this.form.controls.email.setValue(this.form.controls.email.value.trim());
    this.form.markAllAsTouched();
    this.error.set('');
    if (this.form.invalid) return;
    const { name, email, password } = this.form.getRawValue();
    this.sending.set(true);
    this.auth.register({ name, email, password }).pipe(
      takeUntilDestroyed(this.destroyRef), finalize(() => this.sending.set(false)),
    ).subscribe({
      next: () => {
        this.form.reset();
        void this.router.navigate(['/login'], { queryParams: { registered: '1' } });
      },
      error: (error: unknown) => {
        const status = error instanceof HttpErrorResponse ? error.status : -1;
        this.error.set(status === 400 ? 'Revisa los datos ingresados e inténtalo de nuevo.'
          : status === 409 ? 'Este correo ya está registrado. Puedes iniciar sesión con tu cuenta.'
          : status === 0 ? 'No pudimos conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.'
          : 'No pudimos crear tu cuenta. Inténtalo de nuevo más tarde.');
      },
    });
  }
}

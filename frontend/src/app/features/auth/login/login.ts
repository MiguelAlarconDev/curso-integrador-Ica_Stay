import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { SiteHeader } from '../../../shared/site-header/site-header';

@Component({
  selector: 'app-login', imports: [ReactiveFormsModule, RouterLink, SiteHeader],
  templateUrl: './login.html', styleUrl: './login.css', changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly sending = signal(false);
  protected readonly error = signal('');
  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  protected submit(): void {
    if (this.sending()) return;
    this.form.controls.email.setValue(this.form.controls.email.value.trim());
    this.form.markAllAsTouched();
    this.error.set('');
    if (this.form.invalid) return;
    this.sending.set(true);
    this.auth.login(this.form.getRawValue()).pipe(takeUntilDestroyed(this.destroyRef),
      finalize(() => this.sending.set(false)),
    ).subscribe({
      next: () => { this.form.controls.password.reset(); void this.router.navigateByUrl('/'); },
      error: (error: unknown) => {
        this.form.controls.password.reset();
        this.error.set(error instanceof HttpErrorResponse && error.status === 401
          ? 'El correo o la contraseña no son correctos.'
          : error instanceof HttpErrorResponse && error.status === 0
            ? 'No pudimos conectar. Revisa tu conexión e inténtalo de nuevo.'
            : 'No pudimos iniciar sesión. Inténtalo de nuevo más tarde.');
      },
    });
  }
}

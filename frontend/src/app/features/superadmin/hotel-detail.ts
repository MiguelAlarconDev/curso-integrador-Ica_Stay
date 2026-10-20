import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, finalize, map, merge, Observable, of, skip, Subject, switchMap, takeUntil, tap } from 'rxjs';
import { SiteHeader } from '../../shared/site-header/site-header';
import { AuthService } from '../../core/services/auth.service';
import { SuperadminHotelService } from '../../core/services/superadmin-hotel.service';
import { SuperadminHotelDetail } from '../../core/models/superadmin-hotel';

@Component({
  selector: 'app-superadmin-hotel-detail', imports: [SiteHeader, RouterLink, ReactiveFormsModule],
  templateUrl: './hotel-detail.html', styleUrls: ['./create-hotel.css', './hotel-detail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SuperadminHotelDetailPage {
  private readonly api = inject(SuperadminHotelService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly retryEvent = new Subject<void>();
  private id = '';
  protected readonly data = signal<SuperadminHotelDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly missing = signal(false);
  protected readonly message = signal('');
  protected readonly pendingStatus = signal<'ACTIVE' | 'INACTIVE' | null>(null);
  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(180)] }),
    address: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)] }),
    city: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(120)] }),
    description: new FormControl('', { nonNullable: true, validators: Validators.maxLength(5000) }),
  });
  protected readonly fields = [
    { key: 'name', label: 'Nombre', max: 180 }, { key: 'address', label: 'Dirección', max: 255 },
    { key: 'city', label: 'Ciudad', max: 120 }, { key: 'description', label: 'Descripción (opcional)', max: 5000 },
  ] as const;

  constructor() {
    effect(() => { if (!this.auth.session()) { this.data.set(null); void this.router.navigateByUrl('/login'); } });
    merge(this.route.paramMap.pipe(map(params => params.get('id') ?? '')), this.retryEvent.pipe(map(() => this.id)))
      .pipe(tap(id => {
        this.id = id; this.loading.set(true); this.data.set(null); this.error.set(''); this.missing.set(false);
        this.message.set(''); this.pendingStatus.set(null);
      }), switchMap(id => this.api.get(id).pipe(catchError(error => { this.failure(error.status); return of(null); }))),
      takeUntilDestroyed(this.destroyRef)).subscribe(data => {
        this.data.set(data); if (data) this.resetForm(data); this.loading.set(false);
      });
  }
  protected backQuery() {
    const p = this.route.snapshot.queryParamMap;
    return { name: p.get('name'), status: p.get('status'), page: p.get('page') };
  }
  protected retry() { if (!this.loading() && !this.busy()) this.retryEvent.next(); }
  protected save() {
    if (this.busy() || this.pendingStatus() || !this.data()) return;
    for (const field of this.fields) { const c = this.form.controls[field.key]; c.setValue(c.value.trim()); }
    this.form.markAllAsTouched(); this.message.set(''); this.error.set('');
    if (this.form.invalid) return;
    this.mutate(this.api.update(this.id, this.form.getRawValue()), 'Datos del hotel guardados.', true);
  }
  protected askStatus() {
    if (this.busy() || !this.data()) return;
    this.error.set(''); this.message.set('');
    this.pendingStatus.set(this.data()!.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE');
  }
  protected cancelStatus() { if (!this.busy()) this.pendingStatus.set(null); }
  protected confirmStatus() {
    const status = this.pendingStatus();
    if (!status || this.busy()) return;
    this.mutate(this.api.changeStatus(this.id, status), status === 'ACTIVE' ? 'Hotel activado.' : 'Hotel desactivado.', false);
  }
  private mutate(request: Observable<SuperadminHotelDetail>, message: string, reset: boolean) {
    this.busy.set(true); this.error.set(''); this.message.set('');
    request.pipe(takeUntil(this.route.paramMap.pipe(skip(1))), takeUntilDestroyed(this.destroyRef),
      finalize(() => this.busy.set(false))).subscribe({
      next: data => {
        this.data.set(data); if (reset || this.form.pristine) this.resetForm(data);
        this.pendingStatus.set(null); this.message.set(message);
      },
      error: error => { this.pendingStatus.set(null); this.failure(error.status); },
    });
  }
  private resetForm(data: SuperadminHotelDetail) {
    this.form.reset({ name: data.name, address: data.address, city: data.city, description: data.description ?? '' });
  }
  private failure(status: number) {
    if (status === 404) { this.missing.set(true); this.data.set(null); }
    this.error.set(status === 404 ? 'El hotel no existe o ya no está disponible.'
      : status === 400 ? 'Revisa los datos del hotel.' : status === 403 ? 'No tienes permiso para esta operación.'
      : 'No pudimos completar la operación. Inténtalo de nuevo.');
  }
}

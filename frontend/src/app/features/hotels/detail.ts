import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, distinctUntilChanged, map, merge, of, Subject, switchMap, tap } from 'rxjs';
import { HotelService } from '../../core/services/hotel.service';
import { HotelView } from '../../core/models/hotel';
import { SiteHeader } from '../../shared/site-header/site-header';
import { SiteFooter } from '../../shared/site-footer/site-footer';

@Component({
  selector: 'app-hotel-detail', imports: [RouterLink, SiteHeader, SiteFooter],
  templateUrl: './detail.html', styleUrl: './hotels.css', changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HotelDetail implements OnInit {
  private readonly service = inject(HotelService);
  protected readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly retryEvent = new Subject<void>();
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly hotel = signal<HotelView | null>(null);
  ngOnInit(): void {
    merge(this.route.paramMap.pipe(map(params => params.get('id')!), distinctUntilChanged()),
      this.retryEvent.pipe(map(() => this.route.snapshot.paramMap.get('id')!))).pipe(
      tap(() => { this.loading.set(true); this.error.set(''); this.hotel.set(null); }),
      switchMap(id => this.service.get(id).pipe(catchError(error => {
        this.error.set(error.status === 404 ? 'Este hotel no está disponible o no existe.'
          : error.status === 0 ? 'No pudimos conectar con el servidor.' : 'No pudimos cargar el hotel. Inténtalo de nuevo.');
        return of(null);
      }))), takeUntilDestroyed(this.destroyRef),
    ).subscribe(hotel => { this.hotel.set(hotel); this.loading.set(false); });
  }
  protected retry(): void { if (!this.loading()) this.retryEvent.next(); }
}

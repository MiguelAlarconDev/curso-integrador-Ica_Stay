import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HotelService } from '../../core/services/hotel.service';
import { HotelView } from '../../core/models/hotel';
import { SiteHeader } from '../../shared/site-header/site-header';
import { SiteFooter } from '../../shared/site-footer/site-footer';
import { SearchBar } from '../../shared/search-bar/search-bar';

@Component({
  selector: 'app-home',
  imports: [SiteHeader, SiteFooter, SearchBar],
  templateUrl: './home.html',
  styleUrl: './home.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Home implements OnInit {
  private readonly hotelService = inject(HotelService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly hotels = signal<HotelView[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  // Editorial imagery is temporary and does not depict the returned hotels.
  private readonly editorialImages = [
    { photo: '/images/home/hotel-vinedos.webp', alt: 'Botellas de vino sobre una barrica entre hileras de vides', width: 474, height: 316, type: 'principal' },
    { photo: '/images/home/hotel-dunas.webp', alt: 'Piscina rodeada de palmeras y tumbonas, con dunas al fondo', width: 474, height: 316, type: 'secondary' },
    { photo: '/images/home/hotel-ciudad.webp', alt: 'Patio de hotel con piscina y balcones cubiertos de vegetación', width: 474, height: 266, type: 'secondary' },
  ];

  protected readonly stays = computed(() => this.hotels().slice(0, 3).map((hotel, index) => ({
    ...hotel,
    ...this.editorialImages[index],
  })));

  ngOnInit(): void {
    this.hotelService.list().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (page) => {
        this.hotels.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  protected readonly destinations = [
    { name: 'Huacachina', category: 'ENTRE DUNAS', description: 'El oasis y el desierto, en una misma escapada.', photo: '/images/home/huacachina.webp', alt: 'Vista del oasis de Huacachina iluminado al atardecer entre dunas', width: 474, height: 266 },
    { name: 'Viñedos', category: 'TIERRA Y TRADICIÓN', description: 'Otra forma de conocer el valle de Ica.', photo: '/images/home/vinedos.webp', alt: 'Barricas de madera junto a un campo de vides', width: 474, height: 266 },
    { name: 'Paracas', category: 'FRENTE AL PACÍFICO', description: 'El encuentro del desierto con el mar.', photo: '/images/home/paracas.webp', alt: 'Acantilados de arena y formaciones rocosas junto al mar turquesa', width: 1024, height: 585 },
  ];
}

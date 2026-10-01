import { ChangeDetectionStrategy, Component } from '@angular/core';
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
export class Home {
  // Contenido de maqueta: no representa hoteles ni disponibilidad reales.
  protected readonly stays = [
    { name: 'Una casa entre viñedos', location: 'EL VALLE', description: 'Patios abiertos y una pausa en el camino.', photo: '/images/home/hotel-vinedos.webp', alt: 'Botellas de vino sobre una barrica entre hileras de vides', width: 474, height: 316, type: 'principal' },
    { name: 'Un refugio junto a las dunas', location: 'EL DESIERTO', description: 'Un punto de partida para explorar el paisaje.', photo: '/images/home/hotel-dunas.webp', alt: 'Piscina rodeada de palmeras y tumbonas, con dunas al fondo', width: 474, height: 316, type: 'secondary' },
    { name: 'Una estancia en la ciudad', location: 'LA CIUDAD', description: 'Un lugar desde donde recorrer Ica.', photo: '/images/home/hotel-ciudad.webp', alt: 'Patio de hotel con piscina y balcones cubiertos de vegetación', width: 474, height: 266, type: 'secondary' },
  ];

  protected readonly destinations = [
    { name: 'Huacachina', category: 'ENTRE DUNAS', description: 'El oasis y el desierto, en una misma escapada.', photo: '/images/home/huacachina.webp', alt: 'Vista del oasis de Huacachina iluminado al atardecer entre dunas', width: 474, height: 266 },
    { name: 'Viñedos', category: 'TIERRA Y TRADICIÓN', description: 'Otra forma de conocer el valle de Ica.', photo: '/images/home/vinedos.webp', alt: 'Barricas de madera junto a un campo de vides', width: 474, height: 266 },
    { name: 'Paracas', category: 'FRENTE AL PACÍFICO', description: 'El encuentro del desierto con el mar.', photo: '/images/home/paracas.webp', alt: 'Acantilados de arena y formaciones rocosas junto al mar turquesa', width: 1024, height: 585 },
  ];
}

import { Routes } from '@angular/router';

export const adminRoutes: Routes = [
  {
    path: '', pathMatch: 'full', title: 'Resumen | Ica Stay',
    data: { roles: ['HOTEL_ADMIN'], section: 'Resumen' },
    loadComponent: () => import('./overview').then(m => m.Overview),
  },
  {
    path: 'profile', title: 'Mi hotel | Ica Stay',
    data: { roles: ['HOTEL_ADMIN'], section: 'Mi hotel' },
    loadComponent: () => import('./profile/profile').then(m => m.HotelProfile),
  },
  {
    path: 'rooms', title: 'Habitaciones | Ica Stay',
    data: { roles: ['HOTEL_ADMIN'], section: 'Habitaciones' },
    loadComponent: () => import('./rooms/rooms').then(m => m.HotelRooms),
  },
  ...[
    { path: 'reservations', section: 'Reservas', message: 'La consulta administrativa de reservas estará disponible en el incremento 4.4.' },
  ].map(({ path, section, message }) => ({
    path, title: `${section} | Ica Stay`,
    data: { roles: ['HOTEL_ADMIN'], section, message },
    loadComponent: () => import('./section-placeholder').then(m => m.SectionPlaceholder),
  })),
];

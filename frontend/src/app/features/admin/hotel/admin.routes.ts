import { Routes } from '@angular/router';

export const adminRoutes: Routes = [
  {
    path: '', pathMatch: 'full', title: 'Resumen | Ica Stay',
    data: { roles: ['HOTEL_ADMIN'], section: 'Resumen' },
    loadComponent: () => import('./overview').then(m => m.Overview),
  },
  ...[
    { path: 'profile', section: 'Mi hotel', message: 'La gestión del perfil de tu hotel estará disponible en el incremento 4.2.' },
    { path: 'rooms', section: 'Habitaciones', message: 'La gestión de habitaciones estará disponible en el incremento 4.3.' },
    { path: 'reservations', section: 'Reservas', message: 'La consulta administrativa de reservas estará disponible en el incremento 4.4.' },
  ].map(({ path, section, message }) => ({
    path, title: `${section} | Ica Stay`,
    data: { roles: ['HOTEL_ADMIN'], section, message },
    loadComponent: () => import('./section-placeholder').then(m => m.SectionPlaceholder),
  })),
];

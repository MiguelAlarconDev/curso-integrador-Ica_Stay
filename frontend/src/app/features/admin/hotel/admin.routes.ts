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
  {
    path: 'reservations', title: 'Reservas | Ica Stay',
    data: { roles: ['HOTEL_ADMIN'], section: 'Reservas' },
    loadComponent: () => import('./reservations/reservations').then(m => m.HotelReservations),
  },
];

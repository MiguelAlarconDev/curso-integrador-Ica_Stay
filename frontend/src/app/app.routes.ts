import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: 'superadmin/users/:id', title: 'Detalle de usuario | Ica Stay', canActivate: [authGuard], data: { roles: ['SUPER_ADMIN'] },
    loadComponent: () => import('./features/superadmin/user-detail').then(m => m.SuperadminUserDetailPage) },
  { path: 'superadmin/users', title: 'Usuarios | Ica Stay', canActivate: [authGuard], data: { roles: ['SUPER_ADMIN'] },
    loadComponent: () => import('./features/superadmin/users').then(m => m.SuperadminUsers) },
  { path: 'superadmin/hotels/new', title: 'Registrar hotel | Ica Stay', canActivate: [authGuard], data: { roles: ['SUPER_ADMIN'] },
    loadComponent: () => import('./features/superadmin/create-hotel').then(m => m.CreateHotel) },
  { path: 'superadmin/hotels/:id', title: 'Detalle del hotel | Ica Stay', canActivate: [authGuard], data: { roles: ['SUPER_ADMIN'] },
    loadComponent: () => import('./features/superadmin/hotel-detail').then(m => m.SuperadminHotelDetailPage) },
  { path: 'superadmin/hotels', title: 'Hoteles de la plataforma | Ica Stay', canActivate: [authGuard], data: { roles: ['SUPER_ADMIN'] },
    loadComponent: () => import('./features/superadmin/hotels').then(m => m.SuperadminHotels) },
  { path: 'superadmin/dashboard', title: 'Dashboard | Ica Stay', canActivate: [authGuard], data: { roles: ['SUPER_ADMIN'] },
    loadComponent: () => import('./features/superadmin/dashboard').then(m => m.Dashboard) },
  { path: 'my-reservations', title: 'Mis reservas | Ica Stay', canActivate: [authGuard], data: { roles: ['USER'] },
    loadComponent: () => import('./features/reservations/my-reservations').then(m => m.MyReservations) },
  { path: 'my-reservations/:id', title: 'Mi reserva | Ica Stay', canActivate: [authGuard], data: { roles: ['USER'] },
    loadComponent: () => import('./features/reservations/my-reservations').then(m => m.MyReservations) },
  { path: 'hotels', title: 'Hoteles | Ica Stay', loadComponent: () => import('./features/hotels/catalog').then(m => m.HotelCatalog) },
  { path: 'hotels/:id', title: 'Detalle del hotel | Ica Stay', loadComponent: () => import('./features/hotels/detail').then(m => m.HotelDetail) },
  {
    path: 'admin/hotel',
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    data: { roles: ['HOTEL_ADMIN'] },
    loadComponent: () => import('./features/admin/hotel/admin-layout').then(m => m.AdminLayout),
    loadChildren: () => import('./features/admin/hotel/admin.routes').then(m => m.adminRoutes),
  },
  {
    path: 'register',
    title: 'Crear cuenta | Ica Stay',
    loadComponent: () => import('./features/auth/register/register').then(m => m.Register),
  },
  {
    path: 'login',
    title: 'Iniciar sesión | Ica Stay',
    loadComponent: () => import('./features/auth/login/login').then(m => m.Login),
  },
  {
    path: '',
    pathMatch: 'full',
    title: 'Ica Stay | Encuentra dónde quedarte en Ica',
    loadComponent: () => import('./features/home/home').then((m) => m.Home),
  },
];

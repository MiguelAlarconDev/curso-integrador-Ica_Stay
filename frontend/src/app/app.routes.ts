import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
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

import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
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

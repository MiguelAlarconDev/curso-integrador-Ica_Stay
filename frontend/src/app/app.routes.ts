import { Routes } from '@angular/router';

export const routes: Routes = [
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

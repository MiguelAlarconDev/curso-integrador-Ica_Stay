import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Ica Stay | Encuentra dónde quedarte en Ica',
    loadComponent: () => import('./features/home/home').then((m) => m.Home),
  },
];

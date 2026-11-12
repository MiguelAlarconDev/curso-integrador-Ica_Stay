import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Hotel admin landing: real shortcuts only; no metrics endpoint exists for HOTEL_ADMIN yet. */
@Component({
  selector: 'app-admin-overview',
  imports: [RouterLink],
  template: `
    <section aria-labelledby="welcome-title">
      <span class="eyebrow">Tu espacio de administración</span>
      <h2 id="welcome-title">Bienvenido a tu hotel.</h2>
      <p class="intro">Un lugar para acompañar cada estancia. Desde aquí podrás organizar tu hotel, sus habitaciones y sus reservas.</p>
      <div class="shortcuts">
        @for (item of shortcuts; track item.path) {
          <article class="shortcut">
            <span class="icon" aria-hidden="true">{{ item.icon }}</span>
            <h3>{{ item.title }}</h3>
            <p>{{ item.text }}</p>
            <a class="btn btn-outline btn-sm" [routerLink]="item.path">{{ item.action }}</a>
          </article>
        }
      </div>
      <p class="note">Las métricas del hotel se mostrarán cuando exista un servicio que las proporcione.</p>
    </section>
  `,
  styles: `
    h2 { font-size: clamp(2rem, 3vw, 3rem); color: var(--ica-green); }
    .intro { max-width: 58ch; margin-top: 20px; color: var(--ica-muted); }
    .shortcuts { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 230px), 1fr)); gap: 1.25rem; margin-top: 2.25rem; }
    .shortcut { display: flex; flex-direction: column; gap: .6rem; background: var(--ica-white); border: 1px solid var(--ica-line);
      border-top: 3px solid var(--ica-gold); border-radius: var(--radius-lg); padding: 1.4rem; }
    .shortcut .btn { margin-top: auto; align-self: flex-start; }
    .icon { display: grid; place-items: center; width: 44px; height: 44px; border-radius: 50%; background: var(--ica-cream); color: var(--ica-green); font-size: 1.3rem; }
    h3 { font-size: 1.45rem; color: var(--ica-green); overflow-wrap: anywhere; }
    .shortcut p { color: var(--ica-muted); font-size: .92rem; }
    .note { margin-top: 2rem; color: var(--ica-muted); font-size: .85rem; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Overview {
  protected readonly shortcuts = [
    { path: '/admin/hotel/profile', icon: '⌂', title: 'Mi hotel', text: 'Actualiza nombre, descripción, dirección y fotografías.', action: 'Editar perfil' },
    { path: '/admin/hotel/rooms', icon: '▦', title: 'Habitaciones', text: 'Crea, edita, activa o desactiva habitaciones y sus galerías.', action: 'Gestionar habitaciones' },
    { path: '/admin/hotel/reservations', icon: '☷', title: 'Reservas', text: 'Consulta y filtra las reservas recibidas por tu hotel.', action: 'Ver reservas' },
  ];
}

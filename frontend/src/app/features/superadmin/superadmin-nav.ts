import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

/** Section tabs shared by the existing SUPER_ADMIN pages; only implemented routes are listed. */
@Component({
  selector: 'app-superadmin-nav',
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav class="superadmin-nav" aria-label="Secciones de administración">
      <div class="container tabs">
        <span class="label">Panel SUPER_ADMIN</span>
        @for (link of links; track link.path) {
          <a [routerLink]="link.path" routerLinkActive="active" ariaCurrentWhenActive="page">{{ link.label }}</a>
        }
      </div>
    </nav>`,
  styles: `
    .superadmin-nav { background: var(--ica-green); color: var(--ica-cream); }
    .tabs { display: flex; flex-wrap: wrap; align-items: center; gap: .25rem 1.25rem; min-height: 52px; }
    .label { color: var(--ica-gold); font-size: .7rem; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; margin-right: auto; }
    a { padding: .85rem .25rem; color: rgb(250 247 242 / .82); border-bottom: 3px solid transparent; font-weight: 600; font-size: .92rem; }
    a:hover { color: var(--ica-cream); }
    a.active { color: var(--ica-cream); border-bottom-color: var(--ica-gold); }
    a:focus-visible { outline: 2px solid var(--ica-gold); outline-offset: 2px; }
    @media (max-width: 560px) { .label { flex-basis: 100%; padding-top: .6rem; } }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SuperadminNav {
  protected readonly links = [
    { path: '/superadmin/dashboard', label: 'Resumen' },
    { path: '/superadmin/hotels', label: 'Hoteles' },
    { path: '/superadmin/users', label: 'Usuarios' },
  ];
}

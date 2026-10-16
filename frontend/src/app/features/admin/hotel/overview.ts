import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-admin-overview',
  template: `
    <section aria-labelledby="welcome-title">
      <span class="eyebrow">Tu espacio de administración</span>
      <h2 id="welcome-title">Bienvenido a tu hotel.</h2>
      <p class="intro">Un lugar para acompañar cada estancia. Desde aquí podrás organizar tu hotel, sus habitaciones y sus reservas.</p>
      <div class="metrics">
        @for (metric of metrics; track metric) {
          <article><h3>{{ metric }}</h3><p>Próximamente</p><span>Información aún no disponible.</span></article>
        }
      </div>
    </section>
  `,
  styles: `
    h2 { font-size: clamp(2rem, 3vw, 3rem); }
    .intro { max-width: 58ch; margin-top: 20px; color: var(--ica-muted); }
    .metrics { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); margin-top: 40px; border-top: 1px solid var(--ica-line); }
    article { padding: 28px 24px 28px 0; border-bottom: 1px solid var(--ica-line); }
    h3 { font-size: 1.5rem; overflow-wrap: anywhere; }
    article p { margin-top: 20px; color: var(--ica-clay-dark); }
    article span { color: var(--ica-muted); font-size: .8rem; }
    @media (max-width: 540px) { .metrics { grid-template-columns: minmax(0, 1fr); } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Overview {
  protected readonly metrics = ['Habitaciones', 'Reservas', 'Pendientes de pago', 'Disponibilidad'];
}

import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-admin-section-placeholder',
  template: `
    <section aria-labelledby="upcoming-title">
      <span class="eyebrow">Próximamente</span>
      <h2 id="upcoming-title">Estamos preparando este espacio.</h2>
      <p>{{ data()?.['message'] }}</p>
    </section>
  `,
  styles: `
    section { max-width: 64ch; padding-block: 16px 40px; }
    h2 { font-size: clamp(2rem, 3vw, 3rem); }
    p { margin-top: 24px; color: var(--ica-muted); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SectionPlaceholder {
  protected readonly data = toSignal(inject(ActivatedRoute).data);
}

import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AiCatalog } from '../../core/models/ai-chat';

@Component({
  selector: 'app-ai-catalog-cards', imports: [RouterLink, DatePipe],
  templateUrl: './ai-catalog-cards.html', styleUrl: './ai-catalog-cards.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AiCatalogCards {
  readonly catalog = input.required<AiCatalog>();
}

import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

@Component({
  selector: 'app-search-bar',
  imports: [ReactiveFormsModule],
  templateUrl: './search-bar.html',
  styleUrl: './search-bar.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.home-search]': 'home()' },
})
export class SearchBar {
  readonly home = input(false);
  private readonly router = inject(Router);
  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: Validators.maxLength(180) }),
    city: new FormControl('', { nonNullable: true, validators: Validators.maxLength(120) }),
  });
  protected search(): void {
    if (this.form.invalid) return;
    const { name, city } = this.form.getRawValue();
    void this.router.navigate(['/hotels'], { queryParams: { name: name.trim() || null, city: city.trim() || null, page: 0 } });
  }
}

import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { SearchBar } from './search-bar';

describe('Home search form', () => {
  function setup(home = true) {
    TestBed.configureTestingModule({ imports: [SearchBar], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(SearchBar);
    fixture.componentRef.setInput('home', home);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fill = (control: string, value: string) => {
      const field = page.querySelector<HTMLInputElement>(`[formControlName="${control}"]`)!;
      field.value = value;
      field.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };
    const submit = () => page.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    return { page, navigate, fill, submit };
  }

  it.each([false, true])('keeps optional text-only filters and native submit in home=%s', home => {
    const { page, fill, submit, navigate } = setup(home);
    expect(page.classList.contains('home-search')).toBe(home);
    expect(page.querySelectorAll('input').length).toBe(2);
    expect(page.querySelector('input[type="date"], input[type="number"]')).toBeNull();
    expect(page.querySelector('#search-note')?.textContent).toContain('próximamente');
    fill('name', '   '); fill('city', ' Ica '); submit();
    expect(navigate).toHaveBeenCalledWith(['/hotels'], { queryParams: { name: null, city: 'Ica', page: 0 } });
    fill('city', ''); submit();
    expect(navigate).toHaveBeenLastCalledWith(['/hotels'], { queryParams: { name: null, city: null, page: 0 } });
  });

  it.each([['name', 180], ['city', 120]] as const)('preserves the %s length limit and rejects invalid submissions', (control, limit) => {
    const { page, fill, submit, navigate } = setup();
    expect(page.querySelector(`[formControlName="${control}"]`)?.getAttribute('maxlength')).toBe(String(limit));
    fill(control, 'a'.repeat(limit + 1));
    expect(page.querySelector<HTMLButtonElement>('button')!.disabled).toBe(true);
    submit(); expect(navigate).not.toHaveBeenCalled();
    fill(control, 'a'.repeat(limit));
    expect(page.querySelector<HTMLButtonElement>('button')!.disabled).toBe(false);
    submit(); expect(navigate).toHaveBeenCalledOnce();
  });
});

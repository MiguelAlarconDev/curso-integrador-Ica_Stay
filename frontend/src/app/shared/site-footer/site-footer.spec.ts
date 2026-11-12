import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SiteFooter } from './site-footer';

describe('SiteFooter variants', () => {
  it.each([false, true])('preserves existing links and targets with home=%s', home => {
    TestBed.configureTestingModule({ imports: [SiteFooter], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(SiteFooter);
    fixture.componentRef.setInput('home', home);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.classList.contains('home-footer')).toBe(home);
    expect(page.querySelector('.brand')?.getAttribute('href')).toBe('/');
    expect(Array.from(page.querySelectorAll('nav a'), a => a.getAttribute('href')))
      .toEqual(['#alojamientos', '#sobre-el-proyecto', '#contacto']);
    expect(page.querySelector('#sobre-el-proyecto')).not.toBeNull();
    expect(page.querySelector('#contacto')?.textContent).toContain('próximamente');
  });
});

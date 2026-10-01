import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes)],
    })
      .compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('loads the public Home at / with its main sections', async () => {
    const harness = await RouterTestingHarness.create('/');
    const page = harness.routeNativeElement!;
    expect(page.querySelector('h1')?.textContent).toContain('Encuentra dónde quedarte en Ica.');
    expect(page.querySelector('header')).not.toBeNull();
    expect(page.querySelector('#alojamientos')).not.toBeNull();
    expect(page.querySelector('#explorar')).not.toBeNull();
    expect(page.querySelector('footer')).not.toBeNull();
    expect(page.textContent).not.toContain('Hello, frontend');
  });

  it('labels all search controls and keeps unavailable actions disabled', async () => {
    const harness = await RouterTestingHarness.create('/');
    const page = harness.routeNativeElement!;
    const controls = page.querySelectorAll('form input, form select');
    expect(controls.length).toBe(4);
    controls.forEach((control) => {
      expect(page.querySelector(`label[for="${control.id}"]`)?.textContent?.trim()).toBeTruthy();
    });
    expect(page.querySelector<HTMLButtonElement>('form button')?.disabled).toBe(true);
    expect(page.querySelectorAll('header button:disabled').length).toBe(2);
  });
});

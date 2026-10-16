import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';

describe('Public hotel catalog', () => {
  const api = 'http://localhost:8080/api/v1/hotels';
  const hotel = { id: 'hotel-test', name: 'Hotel Oasis', description: 'En el valle', address: 'Calle 1', city: 'Ica' };
  const result = { content: [hotel], page: 0, size: 20, totalElements: 21, totalPages: 2 };
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });
  const http = () => TestBed.inject(HttpTestingController);
  const list = () => http().expectOne(request => request.url === api);
  const page = (h: RouterTestingHarness) => h.routeNativeElement!;
  async function click(h: RouterTestingHarness, selector: string) {
    page(h).querySelector<HTMLElement>(selector)!.click(); await h.fixture.whenStable(); h.detectChanges();
  }

  it('opens anonymously, restores filters, paginates and returns from detail', async () => {
    const h = await RouterTestingHarness.create('/hotels?name=Oasis&city=Ica&page=0');
    expect(page(h).textContent).toContain('Cargando hoteles');
    const request = list(); expect(request.request.params.get('name')).toBe('Oasis');
    expect(request.request.params.get('city')).toBe('Ica'); expect(request.request.params.get('size')).toBe('20');
    request.flush(result); h.detectChanges();
    expect(page(h).textContent).toContain('Hotel Oasis'); expect(page(h).textContent).toContain('Calle 1');
    await click(h, '.next'); const next = list(); expect(next.request.params.get('page')).toBe('1');
    expect(next.request.params.get('city')).toBe('Ica'); next.flush({ ...result, page: 1 }); h.detectChanges();
    await click(h, 'article a'); http().expectOne(`${api}/${hotel.id}`).flush(hotel); h.detectChanges();
    expect(page(h).querySelector('h1')!.textContent).toBe(hotel.name);
    await click(h, '.back'); const back = list(); expect(back.request.params.get('page')).toBe('1'); back.flush({ ...result, page: 1 });
    expect(TestBed.inject(Router).url).toContain('city=Ica');
  });

  it('applies and clears filters, resets page, and cancels obsolete queries', async () => {
    const h = await RouterTestingHarness.create('/hotels?page=2'); const old = list();
    const field = page(h).querySelector<HTMLInputElement>('[formControlName="name"]')!;
    field.value = ' Oasis '; field.dispatchEvent(new Event('input'));
    await click(h, 'form button'); expect(old.cancelled).toBe(true);
    const filtered = list(); expect(filtered.request.params.get('page')).toBe('0');
    expect(filtered.request.params.get('name')).toBe('Oasis'); filtered.flush(result); h.detectChanges();
    await click(h, 'form button'); http().expectNone(request => request.url === api);
    await click(h, 'form button[type="button"]'); const cleared = list(); expect(cleared.request.params.has('name')).toBe(false);
    cleared.flush({ ...result, content: [], totalElements: 0, totalPages: 0 }); h.detectChanges();
    expect(page(h).textContent).toContain('No hay hoteles');
  });

  it('cancels obsolete detail and handles null description', async () => {
    const h = await RouterTestingHarness.create('/hotels/old'); const old = http().expectOne(`${api}/old`);
    await h.navigateByUrl('/hotels/new'); expect(old.cancelled).toBe(true);
    http().expectOne(`${api}/new`).flush({ ...hotel, description: null }); h.detectChanges();
    expect(page(h).textContent).toContain('Sin descripción disponible');
  });

  for (const detail of [false, true]) {
    it.each([0, 404, 500])(`handles ${detail ? 'detail' : 'catalog'} error %s and retries`, async status => {
      const h = await RouterTestingHarness.create(detail ? '/hotels/test' : '/hotels');
      const request = detail ? http().expectOne(`${api}/test`) : list();
      if (status === 0) request.error(new ProgressEvent('error'));
      else request.flush({}, { status, statusText: 'Rejected' });
      h.detectChanges(); expect(page(h).querySelector('[role="alert"]')).not.toBeNull();
      if (detail && status === 404) expect(page(h).textContent).toContain('no está disponible');
      await click(h, '.retry'); (detail ? http().expectOne(`${api}/test`) : list()).flush(detail ? hotel : result);
      h.detectChanges(); expect(page(h).querySelector('[role="alert"]')).toBeNull();
    });
  }

  it('connects the Home search and featured links without losing editorial layout', async () => {
    const h = await RouterTestingHarness.create('/'); list().flush(result); h.detectChanges();
    expect(page(h).querySelector('a[href="/hotels"]')).not.toBeNull();
    expect(page(h).querySelector(`a[href="/hotels/${hotel.id}"]`)).not.toBeNull();
    expect(page(h).querySelectorAll('.stay-main').length).toBe(1);
    await click(h, 'form button'); list().flush(result); expect(TestBed.inject(Router).url).toContain('/hotels');
  });
});

import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AiCatalogCards } from './ai-catalog-cards';
import { AiCatalog } from '../../core/models/ai-chat';

@Component({ template: '' }) class HotelPage {}

describe('AI hotel cards', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [AiCatalogCards], providers: [
    provideRouter([{ path: 'hotels/:id', component: HotelPage }]),
  ] }));
  const data = (availability: 'UNKNOWN' | 'UNAVAILABLE'): AiCatalog => ({
    checkedAt: '2026-10-22T12:00:00Z', roomsRequested: availability !== 'UNKNOWN',
    checkIn: availability === 'UNKNOWN' ? null : '2026-12-15', checkOut: availability === 'UNKNOWN' ? null : '2026-12-17',
    guests: availability === 'UNKNOWN' ? null : 2, hotels: [{ id: '00000000-0000-4000-8000-000000000001', name: 'Oasis', city: 'Ica', availability, rooms: [] }],
  });
  it('navigates to the existing public detail path and never fabricates prices for hotel listings', async () => {
    const fixture = TestBed.createComponent(AiCatalogCards); fixture.componentRef.setInput('catalog', data('UNKNOWN')); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Precio y capacidad no consultados');
    expect(fixture.nativeElement.textContent).toContain('Disponibilidad sin comprobar');
    fixture.nativeElement.querySelector('a').click(); await fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/hotels/00000000-0000-4000-8000-000000000001');
  });
  it('distinguishes no availability for a dated query from an unknown result', () => {
    const fixture = TestBed.createComponent(AiCatalogCards); fixture.componentRef.setInput('catalog', data('UNAVAILABLE')); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Sin habitaciones disponibles');
    expect(fixture.nativeElement.textContent).toContain('2026-12-15');
    expect(fixture.nativeElement.querySelectorAll('[aria-live]').length).toBe(0);
  });
});

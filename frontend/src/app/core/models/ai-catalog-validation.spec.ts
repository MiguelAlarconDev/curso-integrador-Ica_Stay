import { readAiCatalog } from './ai-catalog-validation';

describe('Verified catalog metadata', () => {
  const card = () => ({ checkedAt: '2026-10-22T12:00:00Z', checkIn: null, checkOut: null, guests: null, roomsRequested: true,
    hotels: [{ id: '00000000-0000-4000-8000-000000000001', name: 'Oasis', city: 'Ica', availability: 'UNKNOWN',
      rooms: [{ number: '101', capacity: 2, pricePerNight: '123.45', currency: 'PEN' }] }] });
  it('accepts exact prices with unknown availability', () => {
    expect(readAiCatalog(card())?.hotels[0].rooms[0].pricePerNight).toBe('123.45');
  });
  it.each([undefined, null, 'Hotel 100 PEN', {}, { hotels: [] }])('does not infer cards from %s', value => {
    expect(readAiCatalog(value)).toBeUndefined();
  });
  it('rejects unsafe ids, oversized collections and unverified availability', () => {
    const unsafe = card(); unsafe.hotels[0].id = 'https://evil.test'; expect(readAiCatalog(unsafe)).toBeUndefined();
    const oversized = card(); oversized.hotels = Array(4).fill(oversized.hotels[0]); expect(readAiCatalog(oversized)).toBeUndefined();
    const falseClaim = card(); falseClaim.hotels[0].availability = 'AVAILABLE'; expect(readAiCatalog(falseClaim)).toBeUndefined();
    const price = card(); price.hotels[0].rooms[0].pricePerNight = 'inventado'; expect(readAiCatalog(price)).toBeUndefined();
  });
});

import { AiCatalog } from './ai-chat';

const object = (value: unknown): value is Record<string, unknown> => !!value && typeof value === 'object' && !Array.isArray(value);
const text = (value: unknown, max: number): value is string => typeof value === 'string' && !!value.trim() && value.length <= max;
const date = (value: unknown): value is string => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
  && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;

/** Optional malformed metadata falls back to answer text, never to guessed cards. */
export function readAiCatalog(value: unknown): AiCatalog | undefined {
  if (!object(value) || !text(value['checkedAt'], 40) || !/^\d{4}-\d{2}-\d{2}T/.test(value['checkedAt'])
    || !Number.isFinite(Date.parse(value['checkedAt'])) || typeof value['roomsRequested'] !== 'boolean'
    || !Array.isArray(value['hotels']) || value['hotels'].length > 3) return undefined;
  const dated = date(value['checkIn']) && date(value['checkOut']) && value['checkOut'] > value['checkIn']
    && Number.isInteger(value['guests']) && Number(value['guests']) >= 1 && Number(value['guests']) <= 20;
  if (!dated && !(value['checkIn'] === null && value['checkOut'] === null && value['guests'] === null)) return undefined;
  if (dated && !value['roomsRequested']) return undefined;
  const ids = new Set<string>();
  for (const hotel of value['hotels']) {
    if (!object(hotel) || !text(hotel['id'], 36) || !/^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i.test(hotel['id'])
      || ids.has(hotel['id']) || !text(hotel['name'], 180) || !text(hotel['city'], 120)
      || !Array.isArray(hotel['rooms']) || hotel['rooms'].length > 3) return undefined;
    ids.add(hotel['id']);
    if (!dated && hotel['availability'] !== 'UNKNOWN') return undefined;
    if (dated && !((hotel['availability'] === 'AVAILABLE' && hotel['rooms'].length > 0)
      || (hotel['availability'] === 'UNAVAILABLE' && hotel['rooms'].length === 0))) return undefined;
    if (!value['roomsRequested'] && hotel['rooms'].length) return undefined;
    for (const room of hotel['rooms']) {
      if (!object(room) || !text(room['number'], 40) || !Number.isInteger(room['capacity']) || Number(room['capacity']) < 1
        || Number(room['capacity']) > 2147483647 || (dated && Number(room['capacity']) < Number(value['guests']))
        || !text(room['pricePerNight'], 13) || !/^\d{1,10}(?:\.\d{1,2})?$/.test(room['pricePerNight'])
        || Number(room['pricePerNight']) <= 0 || !text(room['currency'], 3) || !/^[A-Z]{3}$/.test(room['currency'])) return undefined;
    }
  }
  return value as unknown as AiCatalog;
}

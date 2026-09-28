# Contrato mínimo de API — propuesta v0

**Estado:** diseño para MVP; no implementado.  
**Actualizado:** 2026-09-28.  
Base pública: `/api/v1`. JSON, fechas `YYYY-MM-DD`, instantes ISO 8601 en UTC, importes decimales serializados como cadenas. Endpoints autenticados requieren `Authorization: Bearer <token>`.

| Método | Ruta | Acceso | Propósito |
|---|---|---|---|
| POST | `/auth/register` | Público | Crear cuenta USER |
| POST | `/auth/login` | Público | Obtener token |
| GET | `/hotels` | Público | Listar hoteles activos (paginado) |
| GET | `/hotels/{hotelId}/rooms/availability?checkIn=YYYY-MM-DD&checkOut=YYYY-MM-DD&guests=2` | Público | Mostrar habitaciones físicas disponibles |
| POST | `/reservations` | USER | Retener habitación durante 10 minutos |
| GET | `/reservations/me` | USER | Listar reservas propias |
| GET | `/reservations/{reservationId}` | Dueño, admin del hotel, SUPER_ADMIN | Ver reserva autorizada |
| POST | `/reservations/{reservationId}/cancel` | Dueño | Cancelar solo si sigue pendiente |
| GET | `/hotel-admin/reservations` | HOTEL_ADMIN | Reservas del hotel asignado |
| GET | `/admin/reservations` | SUPER_ADMIN | Reservas globales |

La administración de hoteles y habitaciones requiere endpoints propios cuando se escriban sus specs. El pago real añadirá un endpoint de inicio de pago del core y un contrato interno autenticado de confirmación; **no existe un endpoint público para marcar una reserva como pagada**. El frontend no accede directamente a los servicios internos.

## Peticiones y respuestas clave

```http
POST /api/v1/reservations
Authorization: Bearer <token>
Content-Type: application/json

{"roomId":"<uuid>","checkIn":"2026-10-10","checkOut":"2026-10-12","guests":2}
```

```json
{
  "id": "<uuid>",
  "roomId": "<uuid>",
  "checkIn": "2026-10-10",
  "checkOut": "2026-10-12",
  "guests": 2,
  "status": "PENDING_PAYMENT",
  "expiresAt": "2026-10-01T15:10:00Z",
  "totalAmount": "240.00",
  "currency": "PEN"
}
```

Respuesta `201 Created` y cabecera `Location: /api/v1/reservations/<uuid>`. El ejemplo de `expiresAt` es ilustrativo: se calcula en el servidor desde el momento real de creación.

`GET /hotels/{hotelId}/rooms/availability` devuelve una lista paginada de `roomId`, `number`, `capacity`, `pricePerNight`, `estimatedTotal`, `currency`. Son datos informativos y no garantizan una retención posterior.

`GET /reservations/me`, `GET /hotel-admin/reservations` y `GET /admin/reservations` devuelven listas paginadas. `GET /reservations/{id}` devuelve la representación de reserva, calculando estado efectivo `EXPIRED` si venció.

`POST /reservations/{id}/cancel`: `200` con la reserva en `CANCELLED`; no cancela pagos confirmados. Una cancelación repetida puede devolver `200` con `CANCELLED` para facilitar reintentos.

## Errores mínimos

| HTTP | Código de error | Cuándo |
|---|---|---|
| 400 | `INVALID_DATES`, `INVALID_GUESTS` | Validación del cuerpo o consulta |
| 401 | `UNAUTHENTICATED` | Token ausente o inválido |
| 403 | `FORBIDDEN` | Rol o recurso fuera de alcance |
| 404 | `NOT_FOUND` | Recurso inexistente |
| 409 | `ROOM_UNAVAILABLE`, `RESERVATION_NOT_PENDING` | Conflicto de disponibilidad o estado |

Formato: `{"code":"ROOM_UNAVAILABLE","message":"La habitación ya no está disponible","timestamp":"2026-09-28T14:00:00Z"}`. No revelar reservas ajenas en los mensajes de error.

## Seguridad y coherencia

El core obtiene usuario/rol del token; cruza `admin_user_id` para cada hotel y no confía en IDs de usuario aportados por el cliente. Toda mutación que retiene o confirma habitación revalida disponibilidad bajo bloqueo transaccional en PostgreSQL. Redis, si se usa para búsquedas, no autoriza reservas.

Los nombres y formatos son contrato propuesto para comenzar; las specs de autenticación, catálogos y pagos cerrarán detalles antes de codificarlos.

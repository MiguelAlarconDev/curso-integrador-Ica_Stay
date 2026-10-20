# Detalle, edición y estado del hotel — 7.3.3

**Estado:** implementado y aprobado manualmente por el responsable  
**Actualizado:** 2026-10-19

## Contrato y permisos

Solo SUPER_ADMIN: GET/PUT `/api/v1/superadmin/hotels/{id}` y PATCH
`/api/v1/superadmin/hotels/{id}/status`. Ausente: 404; UUID inválido: 400;
sin JWT: 401; USER/HOTEL_ADMIN: 403.
GET, PUT y PATCH devuelven los ocho campos del listado más `description` nullable.
PUT recibe únicamente name, description, address, city; reutiliza UpdateHotelRequest
y sus límites 180/5000/255/120, recorte y descripción vacía → null.
PATCH recibe `{status: "ACTIVE" | "INACTIVE"}`, exactos, obligatorio e idempotente.
Campos ajenos se ignoran; nunca cambian ID, administrador, habitaciones o reservas.
Los contratos de listado y creación no cambian.

## Consistencia

Cada escritura bloquea el hotel hasta commit. La edición HOTEL_ADMIN usa el mismo
bloqueo para evitar sobrescribir el estado con una entidad cargada anteriormente;
sus permisos y campos editables no cambian. La reserva ya verificaba ACTIVE,
pero faltaba sincronización con desactivaciones concurrentes: ahora mantiene
bloqueo compartido de hotel tras el bloqueo existente de habitación. PATCH no
bloquea habitaciones, evitando invertir ese orden. Si reserva obtiene el bloqueo
primero puede terminar; si desactivación lo obtiene primero la reserva recibe 404.
No se modifican reservas previas, estados ni importes; tampoco habitaciones,
cuenta del administrador ni pagos de reservas existentes.

## Interfaz y aceptación

Ruta protegida `/superadmin/hotels/:id`, desde el listado. Datos administrativos
solo lectura y formulario reactivo del hotel; mensajes por campo y bloqueo de
doble envío. Activar/desactivar exige confirmación explícita separada de guardar.
Detalle actualizado con respuesta del servidor; listado vuelve a consultar al
regresar, conservando sus filtros/página. Carga, reintento, 404 y errores seguros.
Pruebas focalizadas de contratos, permisos, límites, propiedad, reservas,
concurrencia y frontend; build Angular. Sin eliminación ni nuevas dependencias.

## Validación ejecutada

- Maven: EditHotelIntegrationTest, ReservationServiceTest,
  AdminHotelHttpIntegrationTest y SuperadminHotelIntegrationTest mediante -Dtest:
  36 pruebas aprobadas. Integración con PostgreSQL 17.11 y fixtures propios limpiados.
  Las siete nuevas pruebas verifican detalle, edición, límites, propiedad, estados,
  permisos, 404, conservación de habitaciones/reservas y concurrencia real.
- Angular: hotel-detail.spec.ts, hotels.spec.ts y profile/profile.spec.ts mediante
  --include y --watch=false: 48 pruebas aprobadas. Carga, edición, errores,
  confirmación/cancelación, doble envío, guards, cambio de ID y recarga del listado.
- npm run build y git diff --check correctos. No se ejecutaron suites completas.
- Recorrido manual de 7.3.3 aprobado por el responsable antes de 7.5.

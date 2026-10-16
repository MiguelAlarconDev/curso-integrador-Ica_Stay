# Gestión del hotel propio — incrementos 1, 2 y 3

**Estado:** implementado y validado; pendiente de revisión  
**Responsable:** por asignar  
**Actualizado:** 2026-10-15

## Objetivo y permisos

Un usuario autenticado con rol `HOTEL_ADMIN` consulta y actualiza exclusivamente el hotel asociado a su UUID mediante `hotels.admin_user_id`. `USER` y `SUPER_ADMIN` no tienen acceso a estas operaciones. No se admite seleccionar hotel ni administrador desde el cliente.

## Contratos y flujo

- `GET /api/v1/admin/hotel`: obtiene el hotel del principal autenticado; devuelve 200 con `HotelView`.
- `PUT /api/v1/admin/hotel`: reemplaza los datos editables del hotel del principal dentro de una transacción; devuelve 200 con `HotelView` actualizado.
- Ambos requieren `Authorization: Bearer <JWT>` válido.

Petición PUT:

```json
{"name":"Hotel del Valle","description":"Alojamiento en Ica","address":"Av. Principal 123","city":"Ica"}
```

Respuesta GET/PUT:

```json
{"id":"uuid-del-hotel","name":"Hotel del Valle","description":"Alojamiento en Ica","address":"Av. Principal 123","city":"Ica"}
```

Nombre, dirección y ciudad son obligatorios y no pueden estar vacíos tras recortar espacios exteriores. Sus máximos son 180, 255 y 120 caracteres respectivamente, conforme al esquema. Descripción es opcional, máximo 5000 caracteres; null, ausencia o texto vacío la eliminan (se almacena null). Se recortan espacios exteriores de todos los campos. PUT exige los tres campos obligatorios; no es una actualización parcial.

El DTO no incluye `id`, `adminUserId` ni `status`; los campos ajenos al DTO se ignoran explícitamente con `@JsonIgnoreProperties(ignoreUnknown = true)` y nunca se asignan a la entidad. Los parámetros de consulta no se usan para seleccionar recursos. La consulta siempre filtra por el identificador del principal, tanto en GET como en PUT.

## Errores

- 401: token ausente, inválido o expirado.
- 403: rol distinto de `HOTEL_ADMIN`.
- 404: administrador sin hotel asignado (GET y PUT).
- 400: datos inválidos, JSON malformado o cuerpo ausente.

## Criterios de aceptación

- Consultar y persistir solo el hotel asignado; dos administradores no pueden acceder al hotel del otro.
- Identificador, asignación y estado permanecen intactos incluso ante campos adicionales enviados por el cliente.
- Validaciones devuelven 400 sin mutaciones parciales.
- Administrador sin hotel recibe 404; roles no autorizados reciben 403.
- GET público de hoteles mantiene su contrato y filtro de hoteles activos.
- Pruebas con JWT, cadena de seguridad, HTTP real y persistencia PostgreSQL; limpieza de fixtures propios.

## Decisiones y pendientes

- Se permite editar el hotel asignado aunque esté inactivo, sin poder activarlo. Su visibilidad pública permanece restringida a ACTIVE.
- Límite de descripción de 5000 caracteres como decisión de aplicación; no requiere cambiar la columna TEXT. Revisable por producto.
- No se crean hoteles ni asignaciones. Concurrencia entre ediciones conserva la política actual (última escritura); versionado queda fuera de este incremento.

## Fuera de alcance del incremento 1

Esquema/migraciones, habitaciones, frontend administrativo, asignación de administradores y cambios de estado.

## Validación ejecutada del incremento 1

`mvn test`: 76 pruebas, 0 fallos, 0 errores, 0 omitidas; compilación correcta.
`AdminHotelHttpIntegrationTest` aporta 12 casos con HTTP real y PostgreSQL:
aislamiento entre dos administradores, actualización persistida, campos inmutables,
roles excluidos, ausencia/token inválido, hotel no asignado, validaciones y límites,
descripción opcional y visibilidad pública de hoteles inactivos.
Los fixtures usan UUID únicos y se eliminan al finalizar cada caso.

## Incremento 2 — habitaciones del hotel propio

### Contratos administrativos

Todos requieren JWT válido y rol `HOTEL_ADMIN`. La identidad procede de
`AuthenticatedUser.id`; se reutiliza `HotelService.findAssigned` y la relación
única `hotels.admin_user_id`. No se aceptan identificadores del cliente para
determinar la pertenencia. Cada búsqueda de habitación filtra por ID **y** hotel.

| Método | Ruta | Respuesta exitosa |
|---|---|---|
| GET | `/api/v1/admin/hotel/rooms` | 200, array (vacío si no hay habitaciones) |
| GET | `/api/v1/admin/hotel/rooms/{id}` | 200, habitación propia |
| POST | `/api/v1/admin/hotel/rooms` | 201, habitación creada y cabecera Location |
| PUT | `/api/v1/admin/hotel/rooms/{id}` | 200, habitación actualizada |
| PATCH | `/api/v1/admin/hotel/rooms/{id}/status` | 200, habitación con estado actualizado |

POST y PUT reciben los mismos campos editables:

```json
{
  "number": "201",
  "description": "Habitación doble con vista al patio",
  "capacity": 2,
  "pricePerNight": 180.50,
  "currency": "PEN"
}
```

PATCH recibe exclusivamente el cambio administrativo:

```json
{"status":"INACTIVE"}
```

Respuesta de detalle, creación, actualización y estado (`AdminRoomView`):

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "number": "201",
  "description": "Habitación doble con vista al patio",
  "capacity": 2,
  "pricePerNight": 180.50,
  "currency": "PEN",
  "status": "ACTIVE"
}
```

El listado devuelve un array de estos objetos, ordenado por número y UUID;
incluye ACTIVE e INACTIVE, sin paginación en este incremento. No se modifica
`RoomView` ni el contrato público. Los importes siguen siendo números JSON como
en la implementación actual, no cadenas como proponía el documento API v0.

### Validaciones y semántica

- Número obligatorio, no vacío, máximo 40 caracteres, sin espacios exteriores:
  se rechazan, no se recortan silenciosamente. La unicidad por hotel distingue
  mayúsculas y minúsculas conforme a `uq_rooms_hotel_number`.
- Descripción opcional, sin límite adicional al TEXT existente; espacios
  exteriores recortados. Ausencia, null o blanco eliminan la descripción.
- Capacidad obligatoria, entero positivo dentro del rango INTEGER de PostgreSQL
  (hasta 2147483647). Se recibe como BigDecimal y se convierte con `intValueExact`
  para impedir el truncamiento silencioso de cantidades fraccionarias.
- Precio obligatorio, positivo, máximo dos decimales y hasta 9999999999.99,
  respetando NUMERIC(12,2). No se redondean valores inválidos.
- Moneda editable: código ISO 4217 reconocido por Java, tres letras mayúsculas;
  ausencia o null usan PEN, tanto en POST como PUT. No hay conversión monetaria.
  El esquema y las respuestas existentes ya admiten moneda por habitación.
- POST siempre crea ACTIVE. PUT reemplaza los campos editables, pero conserva
  ID, hotel y estado. PATCH solo acepta ACTIVE o INACTIVE y es idempotente.
- Campos adicionales (`id`, `hotelId`, `adminUserId`, `status` en POST/PUT)
  se ignoran explícitamente en el DTO y no se asignan a la entidad.
- No existe eliminación física ni estado OCCUPIED.

### Errores

- 400: datos inválidos, cuerpo ausente, JSON malformado, UUID inválido o estado
  distinto de ACTIVE/INACTIVE.
- 401: sin JWT válido.
- 403: USER o SUPER_ADMIN; estos endpoints son exclusivos de HOTEL_ADMIN.
- 404: administrador sin hotel, habitación inexistente o perteneciente a otro hotel.
- 409: número duplicado en el mismo hotel, tanto al crear como al actualizar.

### Reservas, integridad y concurrencia

El esquema Flyway es suficiente: no se agregan migraciones ni dependencias.
La restricción única de PostgreSQL decide los duplicados incluso concurrentes;
`saveAndFlush` permite traducir específicamente `uq_rooms_hotel_number` a 409
dentro de la transacción, sin ocultar otras violaciones de integridad.

PUT, PATCH y la lectura de habitación al reservar utilizan bloqueo pesimista de
escritura sobre la fila de `rooms` hasta terminar la transacción. Así se serializan
la desactivación, cambios de precio/capacidad y reservas de la misma habitación.
Una reserva que obtiene el bloqueo antes de la desactivación puede completarse;
después de la desactivación no puede crearse una nueva. No se cancela ninguna
reserva previa. El bloqueo también serializa las comprobaciones de solapamiento
entre creaciones de reservas concurrentes para una habitación.

No se actualizan filas de `reservations`: `nightly_price_snapshot`, `total_amount`,
moneda, huéspedes y estado históricos permanecen intactos. Bajar capacidad no
invalida reservas anteriores; el nuevo límite se aplica a las nuevas.
El catálogo público y `ReservationService` ya filtran por ACTIVE: se mantiene
el 404 efectivo para intentar reservar una habitación inactiva.

### Criterios de aceptación y pendientes de revisión

- Cada operación solo alcanza habitaciones del hotel autenticado, incluso con
  IDs ajenos en ruta, cuerpo o parámetros.
- Creación 201; duplicados 409 sin escrituras parciales; inválidos 400.
- Listado y detalle incluyen inactivas para administrarlas; la vista pública no.
- Estado inicial ACTIVE; desactivación no cancela reservas y bloquea nuevas.
- Actualizar precio/moneda/capacidad no altera snapshots ni reservas históricas.
- Pruebas HTTP reales con JWT, PostgreSQL, dos hoteles y limpieza de fixtures;
  casos concurrentes para duplicados y reservas solapadas.
- Se conserva la posibilidad del incremento 1 de administrar un hotel inactivo.
- Diferencias previas por resolver en otro incremento: la spec de reservas propone
  retención de 10 minutos y 409 para habitación inactiva; el código y sus pruebas
  usan 15 minutos y 404. Este incremento no cambia esos contratos.
- Fuera de alcance: frontend, eliminación, pagos, asignación de administradores,
  cambios del esquema y nuevas reglas de reservas ajenas a la disponibilidad.

### Validación ejecutada del incremento 2

`mvn test`: **95 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**.
`AdminRoomHttpIntegrationTest` añade 19 casos HTTP reales con PostgreSQL,
incluyendo duplicados y reservas concurrentes, aislamiento por hotel, roles,
validaciones, actualizaciones y conservación de reservas pendientes y confirmadas.
Los datos de prueba se eliminan por los UUID propios al finalizar cada caso.
`git diff --check`: sin errores.

## Incremento 3 — consulta administrativa de reservas

### Alcance y seguridad

Solo lectura, exclusivamente `HOTEL_ADMIN` con JWT. Se obtiene el UUID de
`AuthenticatedUser` y se resuelve su hotel con `HotelService.findAssigned`.
Las consultas filtran en PostgreSQL por `reservations.room_id → rooms.hotel_id`.
No se aceptan identificadores de hotel/administrador del cliente para decidir
pertenencia; parámetros adicionales con esos nombres no cambian el alcance.
Incluye históricos de hoteles, habitaciones y huéspedes inactivos.

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/api/v1/admin/hotel/reservations` | 200, `AdminReservationPage` |
| GET | `/api/v1/admin/hotel/reservations/{id}` | 200, `AdminReservationView` |

Parámetros del listado:

| Parámetro | Regla |
|---|---|
| page | Entero >= 0, predeterminado 0 |
| size | Entero entre 1 y 100, predeterminado 20 |
| status | Opcional; exactamente PENDING_PAYMENT, CONFIRMED, CANCELLED o EXPIRED |
| from | Opcional; fecha inicial inclusiva YYYY-MM-DD |
| to | Opcional; fecha final inclusiva YYYY-MM-DD |

Fechas reales entre los años 0001 y 9999; cadenas vacías o formatos distintos
son inválidos. Si se proporcionan ambas fechas, `from <= to` (un solo día es válido).
Por el límite de offset de JPA, también se rechaza `page * size > 2147483647`.
Orden fijo `createdAt DESC, id DESC`; un parámetro `sort` no lo modifica.

### Semántica de solapamiento

Se filtra por **estancia**, no por creación de reserva:

- Si existe `from`: `checkOut > from`, pues checkOut es exclusivo.
- Si existe `to`: `checkIn <= to`, pues to es inclusivo.
- Ambos filtros y status se combinan con AND.
- Sin uno de los extremos, ese lado queda abierto; sin fechas, no se limita la estancia.

Ejemplo: estancia `[2030-01-10, 2030-01-13)` coincide con rangos de un día
10, 11 o 12; no coincide con el día 9 ni con el día 13. No se suma un día a
`to`, evitando desbordamientos en la fecha máxima.

### DTOs y ejemplo JSON

`AdminReservationPage`: content, page, size, totalElements, totalPages.
`AdminReservationView` se utiliza tanto en content como en el detalle.

```http
GET /api/v1/admin/hotel/reservations?page=0&size=20&status=CONFIRMED&from=2030-01-10&to=2030-01-12
Authorization: Bearer <JWT>
```

```json
{
  "content": [{
    "id": "11111111-1111-1111-1111-111111111111",
    "checkIn": "2030-01-10",
    "checkOut": "2030-01-13",
    "guests": 2,
    "status": "CONFIRMED",
    "expiresAt": "2029-12-20T15:15:00Z",
    "nightlyPriceSnapshot": 125.50,
    "totalAmount": 376.50,
    "currency": "PEN",
    "createdAt": "2029-12-20T15:00:00Z",
    "roomId": "22222222-2222-2222-2222-222222222222",
    "roomNumber": "201",
    "guestUserId": "33333333-3333-3333-3333-333333333333",
    "guestName": "Huésped de ejemplo",
    "guestEmail": "huesped@example.test"
  }],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

El detalle devuelve directamente el objeto interior. `expiresAt` puede ser null.
Importes como números JSON, fechas de estancia ISO y timestamps UTC. Precio,
total y moneda proceden de la reserva, nunca de los precios actuales del cuarto.
Número de habitación, nombre y correo son los valores actuales de las entidades
relacionadas: no existen snapshots de esos datos. No se devuelve `passwordHash`,
JWT, información de pagos ni entidades completas.

### Respuestas HTTP

- 200: listado o detalle; hotel sin reservas/filtros sin resultados devuelve
  content vacío y totales cero. Página fuera del resultado: content vacío,
  conservando los totales del filtro.
- 400: filtros inválidos, fechas imposibles/invertidas o UUID malformado.
- 401: JWT ausente o inválido.
- 403: rol USER o SUPER_ADMIN (no hay excepción para SUPER_ADMIN).
- 404: administrador sin hotel; detalle inexistente o de otro hotel.

### Consultas y expiración

Se reutilizan `ReservationRepository` y `ReservationService`. Proyección JPQL
por constructor de DTO con joins a RoomEntity y UserEntity: no se materializan
entidades de huéspedes ni se hacen consultas por cada fila. Pertenencia,
filtros, orden y paginación se ejecutan en la base. Spring Data realiza la
consulta de contenido y, cuando corresponde, un count con los mismos filtros;
además se resuelve una vez el hotel del administrador. Transacciones readOnly,
sin escrituras, locks adicionales ni cambios al flujo de creación.

El mecanismo existente considera bloqueantes solo CONFIRMED o PENDING_PAYMENT
con expiresAt futuro. No hay job que cambie automáticamente el estado persistido
a EXPIRED. Por ello una pendiente vencida se muestra y filtra como PENDING_PAYMENT
hasta que otro mecanismo cambie su estado; consultar no recalcula ni persiste
estados. La retención efectiva sigue siendo 15 minutos. Reconciliar la spec antigua
de reservas (10 minutos) permanece pendiente, fuera de este incremento.

### Criterios de aceptación

- Listado, totales y detalle aislados por hotel en la consulta SQL.
- Paginación estable incluso con createdAt coincidente; filtros combinables.
- Límites inclusivos del rango y checkOut exclusivo probados.
- JWT/roles, administrador sin hotel y reserva ajena protegidos con 401/403/404.
- Históricos de habitaciones inactivas visibles sin alterar importes ni estados.
- Respuestas limitadas a los 15 campos explícitos del DTO administrativo.
- Pruebas HTTP reales con PostgreSQL y fixtures independientes por UUID.
- Sin frontend, migraciones, nuevas dependencias ni operaciones administrativas
  de confirmación, cancelación, pago o modificación de reservas.

### Validación ejecutada del incremento 3

`mvn test`: **137 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**.
`AdminReservationHttpIntegrationTest` incorpora 42 casos HTTP con PostgreSQL:
pertenencia, roles, históricos, contrato sin campos sensibles, estados persistidos,
validación de filtros, paginación, orden estable y límites de solapamiento.
Las pruebas existentes de creación de reservas y gestión de habitaciones siguen
pasando. `git diff --check`: sin errores.

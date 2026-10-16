# 5.4 — Mis reservas: historial y estados

Estado: en revisión. Lectura exclusiva USER, sin pagos, cancelación ni cambios de estado.

## Contratos

- GET `/api/v1/reservations/me?page=0`: 20 registros por página, orden createdAt DESC,
  id DESC. Page desde cero, desplazamiento máximo Integer.MAX_VALUE; inválidos: 400.
  Respuesta `{content,page,size,totalElements,totalPages}`. No admite tamaño variable.
- GET `/api/v1/reservations/{id}`: detalle propio. Ajena/inexistente: 404; UUID inválido: 400.
- Ambos exigen USER (401/403) y filtran guestUserId del principal JWT en SQL, nunca
  del cliente. Proyección con joins evita N+1; count se limita al mismo propietario.
- DTO de lectura: id, hotelId, hotelName, roomId, roomNumber, checkIn, checkOut, guests,
  status, expiresAt nullable, nightlyPriceSnapshot, totalAmount, currency, createdAt,
  evaluatedAt y holdExpired. No incluye identidad/datos de contacto del huésped.
- holdExpired es true solo si status=PENDING_PAYMENT y expiresAt <= evaluatedAt.
  Se usa un único instante del reloj del servidor por consulta. Estado persistido
  intacto; no hay actualización ni inferencia de pago. Retención original: 15 minutos.
- Incluye hoteles/habitaciones inactivos. Nombres/números actuales; importes históricos.
  POST y contratos administrativos permanecen intactos. Sin migraciones.

## Interfaz y aceptación

Rutas `/my-reservations` y `/my-reservations/:id`, ambas protegidas por authGuard USER.
Lista y detalle usan cargas independientes por ruta; query page preserva retorno.
Estados reales visibles, con aviso separado «Retención vencida» y hora UTC de consulta.
Actualizar consulta reevalúa en servidor; no se altera el estado usando el reloj local.
Carga, vacío, errores/404 y reintentos; nuevas rutas/páginas cancelan lecturas anteriores.
Al perder/cambiar identidad se retiran los datos y se sale de la vista.

Acceso desde navegación USER y éxito/error incierto de 5.3. El historial permite
comprobar reservas recientes, pero ausencia inmediata no demuestra que un POST falló:
no ofrece reenvío automático. No se añade idempotencia ni búsqueda por intento.

Pruebas: aislamiento A/B y manipulación de IDs, permisos, paginación/orden/totales,
inactivos, expiración exacta sin escrituras, rutas/enlaces, estados, errores y cancelación.
Validación manual pendiente: dos cuentas USER, listado/detalle/retorno, actualización
tras vencimiento y pérdida de sesión; revisar móvil y teclado. No crear reservas reales
durante pruebas automatizadas nuevas: fixtures en transacción con rollback.

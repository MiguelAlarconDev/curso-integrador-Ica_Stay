# Panel HOTEL_ADMIN — incrementos 4.1, 4.2 y 4.3

**Estado:** implementado, pendiente de revisión visual del equipo.
**Actualizado:** 2026-10-15.

## Objetivo y alcance

Layout y navegación administrativa standalone con carga diferida. Reutiliza
la paleta y tipografía globales, AuthService, sessionStorage y authGuard.
El incremento 4.2 conecta el perfil del hotel con GET/PUT reales. No hay métricas
inventadas. El incremento 4.3 incorpora habitaciones; reservas sigue previsto para 4.4.

## Rutas y permisos

| Ruta | Sección |
|---|---|
| `/admin/hotel` | Resumen: bienvenida y cuatro indicadores «Próximamente» |
| `/admin/hotel/profile` | Mi hotel: consulta y edición del perfil |
| `/admin/hotel/rooms` | Listado, detalle, registro, edición y estado de habitaciones |
| `/admin/hotel/reservations` | Reservas: aviso del incremento 4.4 |

El padre usa `canActivate` y `canActivateChild` con el authGuard existente.
Padre e hijas declaran `data.roles: ['HOTEL_ADMIN']`. Sin sesión se navega a
`/login`; USER y SUPER_ADMIN regresan a `/` conservando sesión. No se introduce
returnUrl: se conserva la estrategia existente. Login exitoso envía HOTEL_ADMIN
a `/admin/hotel`; USER y SUPER_ADMIN a `/`. Las tres rutas públicas siguen abiertas.

El catálogo ofrece «Mi panel» solo a HOTEL_ADMIN. El layout llama al logout
existente; al quedar vacía la sesión navega a `/login`, también ante expiración
o invalidación de sesión por el interceptor. Los guards comprueban cada navegación
hija. La autorización real continúa siendo responsabilidad del backend.

## Composición y arquitectura

`features/admin/hotel/` contiene:

- `admin-layout.ts/html/css`: navegación lateral, encabezado único con título,
  correo y rol; main con router-outlet y enlace para saltar al contenido.
- `admin.routes.ts`: rutas hijas lazy y metadatos de sección.
- `overview.ts`: cuatro tarjetas informativas sin cifras ficticias.
- `section-placeholder.ts`: presentación compartida de habitaciones y reservas futuras.
- `profile/profile.ts/html/css`: consulta y formulario reactivo del hotel propio.
- `admin-layout.spec.ts`: navegación real de RouterTestingHarness, permisos,
  sesión, enlace activo, rutas públicas y HTTP simulado del perfil.

App conserva su outlet. El layout no incorpora SiteHeader: ese encabezado sigue
en las páginas públicas. En móvil la navegación es compacta, visible en dos
columnas, sin drawer ni menú superpuesto. Correos largos pueden partirse y las
columnas usan minmax(0, 1fr). Controles de al menos 44 px, foco global visible y
aria-current para la ruta activa. Se conserva prefers-reduced-motion global.

## Validación histórica del incremento 4.1

- Las cuatro rutas muestran su título y una única ruta activa.
- HOTEL_ADMIN accede; otros roles y visitantes son redirigidos sin bucles.
- Logout limpia la sesión y sale del panel.
- Login distingue los tres roles; catálogo, login y registro siguen públicos.
- Ninguna pantalla administrativa solicita datos ni presenta métricas reales.
- `npm test -- --watch=false`: 85 pruebas exitosas.
- `npm run build`: correcto, panel y pantallas en chunks lazy.
- Chrome headless sobre build de producción: las cuatro rutas a 320, 375, 768
  y 1440 px sin scroll horizontal, títulos/aria-current correctos y encabezado
  único. Capturas de Resumen revisadas a 320 y 1440 px. Movimiento reducido
  comprobado con scroll-behavior auto y correo largo de prueba.
- `git diff --check`: sin errores.

Las pruebas usan JWT sintéticos y HTTP simulado, sin cuentas ni escrituras reales.
No se validó un login real contra Spring Boot para este incremento.

## Pendientes

Consulta de reservas 4.4 y métricas cuando existan contratos.
Sin cambios en backend, base de datos, roles, interceptor ni formato JWT.

## Incremento 4.3 — Habitaciones

- Vista lazy existente `/admin/hotel/rooms`; listado y detalle en la misma pantalla,
  con carga, vacío, errores y reintento. No se crean rutas públicas nuevas.
- GET `/api/v1/admin/hotel/rooms` devuelve `AdminRoomView[]`; GET `/{id}` devuelve
  un `AdminRoomView`: id, number, description nullable, capacity, pricePerNight,
  currency, status ACTIVE/INACTIVE. No hay paginación ni campos inventados.
- POST a la colección devuelve 201; PUT `/{id}` y PATCH `/{id}/status` devuelven
  200 con el recurso actualizado. Se usa API_BASE_URL y Bearer existente.
- POST/PUT reciben únicamente:
  `{"number":"201","description":null,"capacity":2,"pricePerNight":180.50,"currency":"PEN"}`.
  PATCH recibe únicamente `{"status":"INACTIVE"}` o `{"status":"ACTIVE"}`.
- Formulario reactivo separado, precargado al editar: número obligatorio máximo
  40, sin espacios exteriores según Java strip; capacidad entera positiva hasta
  2147483647; precio positivo máximo 9999999999.99, hasta dos decimales.
  Descripción opcional sin máximo inventado. Moneda inicial PEN; formato de tres
  mayúsculas en cliente y validez ISO 4217 en backend (400 si no existe).
- Cancelar no escribe; errores conservan el formulario. Solo una operación activa:
  no se puede cambiar de habitación mientras se edita/confirma/guarda. Se cancelan
  consultas de detalle pendientes para evitar sobrescribir respuestas de cambios.
- Cambio de estado con confirmación explícita y bloqueo de duplicados. Las
  respuestas actualizan lista y detalle seleccionado sin modificar otras habitaciones.
  Desactivar impide nuevas reservas, no cancela las existentes (regla del backend).
- Mensajes propios para 0, 400, 401, 403, 404, 409 y otros; 401 conserva el flujo
  interceptor/layout hacia login. 404 nunca expone datos de hoteles ajenos.
- Criterios de aceptación: crear, consultar, editar/cancelar, activar/desactivar
  con confirmación/cancelación, preservar datos ante fallos, impedir duplicados,
  actualizar ambas vistas y conservar guards/navegación. Sin cambios backend,
  dependencias, eliminación, reservas ni pagos. Pruebas HTTP simuladas, sin escrituras
  reales. La moneda admite formato en cliente, sin duplicar el catálogo de Java.

## Incremento 4.2 — Mi hotel

### Contrato real

Ruta lazy `/admin/hotel/profile`, protegida con los guards de 4.1.
`AdminHotelService` utiliza `API_BASE_URL` y el interceptor Bearer existente:

- GET `http://localhost:8080/api/v1/admin/hotel` al entrar, una sola solicitud.
- PUT a la misma URL con únicamente name, description, address y city.
- Ambos devuelven `HotelView`: id, name, description nullable, address y city.
  PUT devuelve el recurso actualizado, por lo que no se repite GET al guardar.
- El ID se muestra como texto de solo lectura. El DTO no contiene status ni
  adminUserId: no se inventan indicadores ni controles para esos campos.

```json
{"name":"Hotel del Valle","description":null,"address":"Av. Principal 123","city":"Ica"}
```

Respuesta:

```json
{"id":"11111111-1111-1111-1111-111111111111","name":"Hotel del Valle","description":null,"address":"Av. Principal 123","city":"Ica"}
```

### Formulario y estados

- Name obligatorio máximo 180; address obligatorio máximo 255; city obligatorio
  máximo 120; description opcional máximo 5000. Longitudes evaluadas después
  de recortar espacios exteriores según Java String.strip, incluida su distinción
  de espacios no separables. Descripción vacía o solo espacios se envía como null.
- GET inicializa el formulario pristine. Guardar exige cambios efectivos válidos;
  agregar solo espacios exteriores no cuenta como cambio. Las ayudas y errores
  están junto a los campos con labels, aria-describedby y aria-invalid.
- Mientras guarda se deshabilita el fieldset y se bloquean envíos duplicados.
  El éxito utiliza el recurso devuelto, restablece dirty y muestra confirmación.
- Cancelar restaura la última respuesta GET/PUT, limpia mensajes y dirty.
- Si falla PUT se conservan las ediciones y no se anuncia éxito.
- Carga, guardado y éxito usan estados accesibles. Error GET ofrece reintento,
  sin mantener simultáneamente solicitudes duplicadas.
- 404 indica administrador sin hotel y ofrece reintentar; 0 indica conexión;
  400 indica validación; 403 indica falta de permisos sin cerrar sesión;
  401 lo procesa el interceptor, limpia sesión y el layout navega a login.
  5xx/otros muestran mensajes propios. 409 tiene mensaje defensivo de conflicto,
  aunque el endpoint actual no define ese estado de negocio.
- Los errores nunca muestran detalles internos de Spring Boot.

### Pruebas y límites

`npm test -- --watch=false`: **107 pruebas exitosas**. Incluyen GET/PUT, payload
exacto, Bearer, límites, normalización, Cancelar, dirty, duplicados, errores y
regresión de las rutas y roles. `npm run build`: correcto, perfil lazy.
Chrome headless a 320, 375, 768 y 1440 px sin overflow horizontal, con encabezado
único y movimiento reducido. Respuestas interceptadas únicamente en la herramienta
de revisión; la aplicación real no contiene mocks. No se probó un PUT real ni se
modificó PostgreSQL. No hay control optimista de versiones en el contrato actual.

### Prueba manual

1. Iniciar backend en 8080 y frontend en 4200; autenticar un HOTEL_ADMIN con hotel.
2. Abrir `/admin/hotel/profile`; verificar GET 200 y los datos reales.
3. Probar campos vacíos, espacios y longitudes excesivas: Guardar queda deshabilitado.
4. Editar y Cancelar: se recuperan los valores originales sin PUT.
5. Editar y Guardar: comprobar un PUT con cuatro campos, respuesta 200,
   confirmación y botón deshabilitado. Recargar para verificar persistencia.
   Este paso sí modifica el hotel real y debe realizarse con datos de desarrollo.
6. Detener el backend para comprobar errores de consulta/reintento y guardado;
   las ediciones permanecen tras el error de guardado. Reactivarlo y reintentar.
7. Con administrador sin hotel comprobar 404; con sesión inválida comprobar
   salida a login; USER/SUPER_ADMIN siguen bloqueados por el guard.

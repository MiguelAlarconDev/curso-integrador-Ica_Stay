# Frontend

## Reserva pendiente — incremento 5.3

Seleccionar una habitación abre un resumen con importe estimado y confirmación
explícita. Solo USER puede enviar POST `/api/v1/reservations`, únicamente roomId,
checkIn, checkOut y guests. La respuesta real muestra PENDING_PAYMENT, importe y
expiresAt UTC; no confirma pago. Retención de 15 minutos sin cambios.
Visitantes van a login con retorno limitado a detalle de hotel y criterios públicos;
al volver se reconsulta y se exige seleccionar/confirmar de nuevo. No hay JWT en URL.
Errores 400/401/403/404/409 tienen mensajes propios. Red/5xx se consideran resultado
incierto, sin reenvío automático. Sin pagos, historial ni cancelación.
Validación manual: USER de desarrollo, consulta/selección/confirmación, doble clic,
retorno de login, conflicto y expiración de sesión. Confirmar sí crea una reserva.

## Disponibilidad — incremento 5.2

El detalle `/hotels/:id` incorpora consulta pública de habitaciones por entrada,
salida y huéspedes. Valida entrada desde hoy UTC, salida posterior y entero positivo.
RoomService envía los tres parámetros a GET `/api/v1/hotels/{id}/rooms` y presenta
RoomView sin campos inventados. Editar cancela peticiones y retira resultados;
carga, vacío, errores y reintento son independientes del hotel. Consultar no reserva.
Se conserva el retorno al catálogo. Retención backend existente: 15 minutos.
Revisión manual: hotel activo, fechas futuras y capacidad válida; probar vacío,
edición durante carga, error/reintento y navegación de retorno. Sin pagos ni historial.

## Catálogo público — incremento 5.1

`/hotels` busca por nombre parcial y ciudad exacta, con 20 resultados por página.
`name`, `city` y `page` se conservan en la URL al paginar, abrir `/hotels/:id`
y volver. Aplicar/limpiar reinician la página; consultas obsoletas se cancelan.
HotelService reutiliza GET `/api/v1/hotels` y GET `/api/v1/hotels/{id}`.
Home mantiene su composición y fotografías referenciales; incluye enlaces al
catálogo/detalle y buscador funcional solo de nombre/ciudad. No hay disponibilidad.
Catálogo y detalle ofrecen carga, vacío/error, reintento y mensaje específico 404.
No requieren sesión; autenticación y administración permanecen sin cambios.

Para revisión: abrir `/hotels` sin sesión, combinar filtros, paginar, consultar un
hotel y volver; recargar y usar Atrás. Probar búsqueda vacía, ID inexistente y
servidor detenido/reintento. Revisar móvil y teclado. Contratos y límites en
[la spec 5.1](../docs/specs/frontend/user-hotel-catalog.md).

## Autenticación

`/login` es pública y envía únicamente `{ email, password }` a `POST /api/v1/auth/login` usando `API_BASE_URL`. La respuesta real es `{ token, tokenType: "Bearer" }`. No hay refresh token ni logout de servidor.

`/register` es pública y usa el mismo diseño editorial del Login. El formulario valida nombre obligatorio (máximo 160), correo válido (máximo 254), contraseña de al menos 8 caracteres y confirmación coincidente. `AuthService.register()` envía únicamente `{ name, email, password }` a `POST /api/v1/auth/register`; la confirmación nunca sale del formulario. Mientras se envía, se bloquean cambios y envíos duplicados. Los errores 400, 409, de conexión y generales se muestran con mensajes propios.

Tras el registro, se redirige a `/login?registered=1`, que muestra una confirmación informativa sin datos personales en la URL. Este indicador no prueba identidad ni concede permisos. No se inicia sesión, no se guarda la contraseña y una sesión previa permanece intacta. Login incluye «Crear cuenta» y Registro incluye «Ya tengo una cuenta».

Para probar manualmente: iniciar backend y frontend, abrir `http://localhost:4200/register`, probar campos vacíos y contraseñas distintas; después registrar un correo nuevo y verificar el 201, la redirección y el aviso. Iniciar sesión con esa cuenta; repetir el registro del correo para comprobar 409. Probar el error de conexión con el backend detenido. Estas comprobaciones manuales sí crean una cuenta; las pruebas unitarias usan HTTP simulado y no modifican PostgreSQL.

`AuthService` mantiene una sesión con signals de solo lectura y guarda exclusivamente el JWT bajo `icastay.auth.token` en `sessionStorage`. La recarga restaura la sesión si el payload contiene `sub`, `userId`, `role`, `iat` y `exp` válidos. Los roles admitidos son `USER`, `HOTEL_ADMIN` y `SUPER_ADMIN`. Se elimina la sesión al expirar, al cerrar sesión o ante un 401 de la petición enviada con el token todavía vigente. Si el navegador bloquea storage, la sesión funciona solo en memoria. El JWT es accesible desde JavaScript; esta persistencia no equivale a una cookie HttpOnly.

La utilidad JWT comprueba estructura, Base64URL, claims, rol y expiración; no verifica la firma. Spring Security sigue siendo la autoridad. Logout no revoca el token en el servidor y no existe renovación automática.

El interceptor funcional agrega Bearer solo al origen y prefijo `/api/` de la API configurada, excluyendo login, registro, archivos estáticos y otros dominios. No usa `withCredentials`. Un 401 de login o registro no elimina una sesión previa, un 401 de una petición antigua no elimina una sesión nueva y un 403 conserva la sesión.

`authGuard` protege las rutas administrativas con `canActivate` y `canActivateChild`, y admite `data.roles`. Sin sesión devuelve `/login`; con rol insuficiente devuelve `/` sin cerrar sesión. Home, Login y Registro siguen siendo públicas.

El Login utiliza la paleta global y una composición editorial con fotografía local. Tras autenticarse, HOTEL_ADMIN navega a `/admin/hotel`; USER y SUPER_ADMIN a `/`. El Header muestra el email, permite cerrar sesión y ofrece «Mi panel» a HOTEL_ADMIN. Los enlaces del Header vuelven a las secciones de Home desde Login.

Se comprobó el Login en Chrome a 320, 375, 768, 1024 y 1440 px, sin desbordamiento horizontal, con imagen local cargada, validación de campos vacíos y movimiento reducido respetado. El mínimo global del body se ajustó para admitir el ancho útil cuando hay barra de desplazamiento vertical. `frontend/.gitignore` exceptúa los DTO TypeScript de la regla global `models/`, que los ocultaba junto con los modelos descargables.

Validación: `npm run build` y `npm test -- --watch=false`. Los JWT de pruebas son fixtures sin firma criptográfica usados exclusivamente en unit tests con HTTP simulado. No crean reservas. Para la verificación manual real, iniciar sesión con un usuario existente, recargar, comprobar Header y logout. Con un usuario `USER`, una solicitud de diagnóstico a `POST /api/v1/reservations` con `{}` debe superar autenticación y devolver 400 sin crear reserva; hoteles es público y no demuestra por sí solo la aceptación del token. No se dispone de credenciales de prueba en esta implementación.

## Panel HOTEL_ADMIN — incremento 4.1

Layout padre lazy con router-outlet y cuatro rutas hijas:
`/admin/hotel` (Resumen), `/admin/hotel/profile` (Mi hotel),
`/admin/hotel/rooms` (Habitaciones), `/admin/hotel/reservations` (Reservas).
Exclusivo de HOTEL_ADMIN mediante el guard existente en padre e hijas.

`features/admin/hotel/` contiene layout, rutas, resumen y un componente informativo
compartido para las secciones futuras. Navegación lateral en escritorio y
compacta en móvil; título, correo, rol, foco visible y ruta activa accesible.
No duplica el header público. Logout usa AuthService y redirige a `/login`;
la pérdida de sesión por expiración también abandona el panel.

El Resumen mantiene indicadores «Próximamente» sin estadísticas ficticias.
El perfil se conecta en 4.2; habitaciones y reservas quedan pendientes para
4.3 y 4.4. Contrato y criterios en [la spec del panel](../docs/specs/frontend/hotel-admin.md).

Validación de este incremento: 85 pruebas exitosas con `npm test -- --watch=false`
y build correcto con `npm run build`. Para revisión manual, iniciar sesión con
un HOTEL_ADMIN existente, recorrer las cuatro rutas, regresar al catálogo y usar
«Mi panel», luego cerrar sesión. Repetir el acceso directo sin sesión y con USER
o SUPER_ADMIN para verificar las redirecciones. Este incremento no crea cuentas.
Chrome headless comprobó las cuatro secciones a 320, 375, 768 y 1440 px, sin
desbordamiento horizontal y con movimiento reducido; sesión sintética local,
sin llamadas al backend. Se revisaron capturas móvil y escritorio.

## Mi hotel — incremento 4.2

`/admin/hotel/profile` consulta y actualiza el hotel de la identidad autenticada.
`AdminHotelService` usa GET/PUT `/api/v1/admin/hotel` sobre `API_BASE_URL`, con el
interceptor JWT existente. Se reutiliza HotelView; PUT recibe únicamente name,
description, address y city, y devuelve el recurso actualizado sin un GET adicional.

Formulario reactivo: nombre/dirección/ciudad obligatorios, máximos 180/255/120;
descripción opcional máximo 5000. Normalización equivalente a String.strip del
backend; descripción vacía se envía null. ID solo lectura; estado y propietario
no se muestran porque no están en el DTO. Guardar requiere cambios válidos y evita
duplicados. Cancelar restaura los últimos datos guardados; fallos conservan ediciones.

Carga y guardado visibles, reintento de GET, mensaje de hotel no asignado para
404, validación 400, permisos 403, conexión 0 y errores generales. Un 401 deja
actuar al interceptor/layout y vuelve a login. 409 tiene manejo defensivo aunque
el contrato actual no define conflictos. No se cambió backend ni PostgreSQL.

Validación: 107 pruebas exitosas, build correcto y revisión Chrome a 320, 375,
768 y 1440 px sin overflow. HTTP simulado en tests y en revisión visual; no se
verificó guardado real contra Spring Boot. Para probar manualmente, iniciar sesión
con HOTEL_ADMIN, abrir Mi hotel, editar y Cancelar; luego editar y Guardar,
comprobar PUT 200 y recargar. Este último paso modifica datos reales. Probar
campos inválidos, backend detenido/reintento y administrador sin hotel.

## Habitaciones — incremento 4.3

`/admin/hotel/rooms` permite listar, consultar, crear, editar y activar/desactivar
habitaciones del hotel autenticado. Reutiliza guards HOTEL_ADMIN, interceptor JWT
y API_BASE_URL. No recibe hotelId ni adminUserId del cliente.

`AdminRoomService` utiliza GET/POST `/api/v1/admin/hotel/rooms`, GET/PUT `/{id}`
y PATCH `/{id}/status`. El DTO real contiene id, number, description nullable,
capacity, pricePerNight, currency y status (ACTIVE/INACTIVE). El listado es un array.

El formulario separado valida número (1–40, sin espacios exteriores), capacidad
entera positiva hasta 2147483647, precio positivo hasta 9999999999.99 con dos
decimales y formato de moneda ISO 4217 (PEN inicial; código validado por backend).
Descripción opcional. POST/PUT envían solo los cinco campos editables.
Cancelar no envía solicitudes. Guardado y confirmación de estado bloquean
duplicados y el cambio de habitación; los errores conservan datos. Un 401 utiliza
el cierre de sesión existente; 403 no cierra sesión. 404, 409, red y errores de
servidor tienen mensajes propios sin exponer detalles internos.

Las respuestas actualizan listado y detalle; el estado exige confirmación antes
del PATCH. Desactivar no cancela reservas existentes. No hay eliminación ni
gestión de reservas. El backend sigue comprobando pertenencia.

Prueba manual: iniciar sesión como HOTEL_ADMIN, abrir Habitaciones, crear una
habitación, consultar detalle, editar y cancelar; luego editar y guardar.
Probar número duplicado y datos inválidos. Desactivar primero cancelando la
confirmación y luego confirmando; reactivar y recargar para verificar persistencia.
Estas acciones sí modifican datos reales. Las pruebas automatizadas usan HTTP
simulado: `npx ng test --watch=false`; compilación: `npm run build`.

## Reservas del hotel — incremento 4.4

`/admin/hotel/reservations` consulta reservas con paginación de 20 elementos,
filtros aplicables/limpiables y detalle independiente dentro de la pantalla.
Reutiliza guards HOTEL_ADMIN, API_BASE_URL e interceptor JWT.

GET `/api/v1/admin/hotel/reservations?page=0&size=20` devuelve
`{content,page,size,totalElements,totalPages}`; GET `/{id}` devuelve el detalle.
Filtros opcionales reales: status (PENDING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED),
from y to. Las fechas buscan estancias coincidentes: checkOut > from y checkIn <= to.
No son filtros de entrada/salida exactas. Cambiar filtros reinicia página y cancela
consultas obsoletas; paginar conserva filtros aplicados. No hay filtrado local.

Se muestran habitación, titular, fechas, estado e importes reales. El detalle
incluye huéspedes, IDs, precio histórico por noche, creación y expiración nullable.
Instantes en UTC; fechas de estancia sin desplazamiento horario. El estado procede
del servidor. Hay carga, vacío, errores HTTP/red y reintento. No hay pagos,
cancelaciones ni cambios de estado; habitaciones permanece intacto.

Validación manual: iniciar sesión como HOTEL_ADMIN y abrir Reservas; aplicar estado
y rango de estancias, paginar, limpiar y consultar detalles. Volver al listado
conserva página y filtros. Probar servidor detenido/reintento y acceso sin sesión
o con USER/SUPER_ADMIN. La paginación requiere más de 20 reservas coincidentes.
Pruebas automatizadas con HTTP simulado: `npx ng test --watch=false`;
compilación: `npm run build`. La integración real queda para revisión manual.

## Integración inicial con API

`app.config.ts` registra `provideHttpClient()`. El token `API_BASE_URL` de `core/api.config.ts` centraliza la URL local y puede sobrescribirse en los providers al desplegar en otro entorno.

Al cargar `/`, `Home.ngOnInit()` se suscribe a `HotelService.list()` y solicita `GET http://localhost:8080/api/v1/hotels?page=0&size=20`. Los modelos reflejan `HotelView` y `HotelPage` del backend; `description` es nullable según la entidad y la migración. La Home guarda el resultado en signals y presenta estados de carga, error y respuesta vacía, sin bloquear el resto de la página.

Se muestran como máximo los tres primeros hoteles en el orden recibido (el backend ordena por nombre e ID). Las fotografías editoriales se asignan por posición y no representan los hoteles reales. Una respuesta bloqueada por CORS se muestra como error de carga; CORS es responsabilidad del backend y no se modifica en esta iteración.

## Home pública — iteración visual 2

Implementa [la SPEC de Home](../docs/specs/frontend/home.md) en `/`, con carga diferida mediante Angular Router. `app.html` contiene el outlet; `features/home/` compone la página y `shared/` contiene header, buscador y footer.

Decisiones de presentación para esta iteración:

- Las siete fotografías locales de `public/images/home/` sustituyen los marcadores visuales siguiendo el mapeo de la SPEC. Se mantienen las alturas y la composición editorial, con `object-fit: cover`, textos alternativos y carga diferida salvo el Hero, que tiene prioridad alta. No se solicitan imágenes externas. El Hero original mide 280 × 180 px y puede perder nitidez al ampliarse.
- Los textos de los alojamientos provienen ahora de la API; las fotografías continúan siendo referenciales. No se muestran precios ni disponibilidad que el DTO no proporcione.
- Buscar y Mis reservas permanecen deshabilitados y acompañados de avisos de disponibilidad futura. Iniciar sesión enlaza ahora a `/login`.
- La navegación usa anclas a secciones existentes. Sobre el proyecto y Contacto apuntan a notas del footer; no se inventa un correo de contacto.
- Los estilos reutilizan los tokens globales. Se conserva el foco visible de los campos y se respeta la preferencia de movimiento reducido.

Validación desde esta carpeta: `npm run build` y `npm test -- --watch=false`. Las tres pruebas existentes verifican la ruta pública, secciones, labels y acciones aún no disponibles. En la iteración visual 2 también se comprobó el build en Chrome headless a 320, 375, 768, 1024 y 1440 px: las siete imágenes cargan, tienen texto alternativo y usan `object-fit: cover`, sin desbordamiento horizontal. Se revisaron capturas móvil y desktop y el desplazamiento sin animación con movimiento reducido. Esta comprobación puntual no agrega dependencias ni una suite de navegador al proyecto.

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 22.2.0.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.

# Frontend

## Autenticación

`/login` es pública y envía únicamente `{ email, password }` a `POST /api/v1/auth/login` usando `API_BASE_URL`. La respuesta real es `{ token, tokenType: "Bearer" }`. No hay registro, refresh token ni logout de servidor.

`AuthService` mantiene una sesión con signals de solo lectura y guarda exclusivamente el JWT bajo `icastay.auth.token` en `sessionStorage`. La recarga restaura la sesión si el payload contiene `sub`, `userId`, `role`, `iat` y `exp` válidos. Los roles admitidos son `USER`, `HOTEL_ADMIN` y `SUPER_ADMIN`. Se elimina la sesión al expirar, al cerrar sesión o ante un 401 de la petición enviada con el token todavía vigente. Si el navegador bloquea storage, la sesión funciona solo en memoria. El JWT es accesible desde JavaScript; esta persistencia no equivale a una cookie HttpOnly.

La utilidad JWT comprueba estructura, Base64URL, claims, rol y expiración; no verifica la firma. Spring Security sigue siendo la autoridad. Logout no revoca el token en el servidor y no existe renovación automática.

El interceptor funcional agrega Bearer solo al origen y prefijo `/api/` de la API configurada, excluyendo login, archivos estáticos y otros dominios. No usa `withCredentials`. Un 401 de login no elimina una sesión previa, un 401 de una petición antigua no elimina una sesión nueva y un 403 conserva la sesión.

`authGuard` está disponible para futuras rutas con `canActivate: [authGuard]` y `data: { roles: ['USER'] }` opcional. Sin sesión devuelve `/login`; con rol insuficiente devuelve `/` sin cerrar sesión. Home y Login siguen siendo públicas y no se añaden pantallas protegidas ficticias.

El Login utiliza la paleta global y una composición editorial con fotografía local. Tras autenticarse navega a `/`; el Header muestra el email y permite cerrar sesión. Los enlaces del Header vuelven a las secciones de Home desde Login.

Se comprobó el Login en Chrome a 320, 375, 768, 1024 y 1440 px, sin desbordamiento horizontal, con imagen local cargada, validación de campos vacíos y movimiento reducido respetado. El mínimo global del body se ajustó para admitir el ancho útil cuando hay barra de desplazamiento vertical. `frontend/.gitignore` exceptúa los DTO TypeScript de la regla global `models/`, que los ocultaba junto con los modelos descargables.

Validación: `npm run build` y `npm test -- --watch=false`. Los JWT de pruebas son fixtures sin firma criptográfica usados exclusivamente en unit tests con HTTP simulado. No crean reservas. Para la verificación manual real, iniciar sesión con un usuario existente, recargar, comprobar Header y logout. Con un usuario `USER`, una solicitud de diagnóstico a `POST /api/v1/reservations` con `{}` debe superar autenticación y devolver 400 sin crear reserva; hoteles es público y no demuestra por sí solo la aceptación del token. No se dispone de credenciales de prueba en esta implementación.

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

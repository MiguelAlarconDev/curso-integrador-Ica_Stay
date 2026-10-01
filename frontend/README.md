# Frontend

## Home pública — iteración visual 2

Implementa [la SPEC de Home](../docs/specs/frontend/home.md) en `/`, con carga diferida mediante Angular Router. `app.html` contiene el outlet; `features/home/` compone la página y `shared/` contiene header, buscador y footer.

Decisiones de presentación para esta iteración:

- Las siete fotografías locales de `public/images/home/` sustituyen los marcadores visuales siguiendo el mapeo de la SPEC. Se mantienen las alturas y la composición editorial, con `object-fit: cover`, textos alternativos y carga diferida salvo el Hero, que tiene prioridad alta. No se solicitan imágenes externas. El Hero original mide 280 × 180 px y puede perder nitidez al ampliarse.
- Los alojamientos ilustrativos están localizados en `home.ts`; no representan oferta real, precios ni disponibilidad.
- Buscar, Mis reservas e Iniciar sesión permanecen deshabilitados y acompañados de avisos de disponibilidad futura. Los campos son editables; no se ejecutan búsquedas ni se almacenan datos.
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

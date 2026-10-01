# Ica Stay — Home pública

## Estado
Acordado

## Objetivo

Implementar la página principal pública de Ica Stay en Angular 22.

Ica Stay es una plataforma web de búsqueda y reserva de alojamientos en Ica, Perú. La interfaz debe transmitir una identidad propia relacionada con el desierto, arquitectura, viñedos y turismo de Ica, evitando la apariencia genérica de interfaces generadas por IA.

## Dirección visual

Concepto: "Ica contemporáneo".

La interfaz debe sentirse editorial, cálida, turística y sobria.

Usar obligatoriamente el sistema visual existente en:

`frontend/src/styles.css`

Especialmente las variables:

- `--ica-ink`
- `--ica-sand`
- `--ica-paper`
- `--ica-clay`
- `--ica-olive`
- `--ica-muted`
- `--ica-line`

No redefinir una segunda paleta global.

## Evitar

No utilizar:

- Gradientes azul/morado.
- Glassmorphism.
- Neomorphism.
- Sombras exageradas.
- Bordes excesivamente redondeados.
- Grandes grupos de cards idénticas.
- Emojis como iconos.
- Elementos decorativos sin función.
- Estadísticas ficticias.
- Testimonios ficticios.
- Textos comerciales exagerados.
- Diseño típico de dashboard SaaS.
- Bootstrap.
- Tailwind.
- Librerías completas de componentes visuales.

No modificar el backend.

## Header

Crear un header limpio.

Izquierda:

ICA STAY

Debe funcionar como logotipo tipográfico temporal.

Centro/derecha:

- Alojamientos
- Explorar Ica
- Mis reservas

Derecha:

- Iniciar sesión

El header debe integrarse visualmente con la página y no parecer una barra de navegación de dashboard.

## Hero

Crear una composición editorial asimétrica.

Texto principal:

"Encuentra dónde quedarte en Ica."

Texto secundario:

"Hoteles y alojamientos para descubrir la región a tu manera."

Debe existir una zona visual grande preparada para fotografía turística.

No utilizar un hero convencional centrado con título + subtítulo + dos botones.

## Buscador

Incluir un buscador visible dentro de la Home.

Campos:

- Destino
- Llegada
- Salida
- Huéspedes

Botón:

"Buscar"

Por ahora el buscador puede ser visual y no necesita consumir el backend.

Destino puede mostrar inicialmente:

"Ica"

Debe estar preparado para conectar funcionalidad posteriormente.

## Sección de alojamientos

Título:

"Hospedajes para descubrir Ica"

Crear una composición visual editorial con alojamientos destacados.

Por ahora se permite utilizar contenido temporal claramente localizado en el componente mientras posteriormente se conecta al backend.

Evitar una cuadrícula genérica de tres cards idénticas.

Debe existir jerarquía entre el alojamiento principal y los secundarios.

## Explorar Ica

Crear una sección visual para:

- Huacachina
- Viñedos
- Paracas

Debe servir posteriormente como acceso a búsquedas o contenido relacionado con esos destinos.

## Footer

Footer sencillo.

Incluir:

- Ica Stay
- Alojamientos
- Sobre el proyecto
- Contacto

No crear un footer excesivamente grande.

## Arquitectura Angular

No implementar toda la Home directamente en `app.html`.

Crear:

`features/home/`

La Home debe ser un componente independiente.

Crear componentes reutilizables cuando exista una separación clara, especialmente para:

- Header
- Search bar
- Footer

Configurar `/` para mostrar Home mediante Angular Router.

`app.html` debe actuar principalmente como shell de navegación mediante `router-outlet`.

Usar la sintaxis y patrones actuales de Angular 22.

## Responsive

La página debe funcionar correctamente en:

- Desktop
- Tablet
- Mobile

En móvil:

- El hero debe reorganizarse verticalmente.
- El buscador debe adaptarse a una columna.
- No debe existir scroll horizontal.
- La jerarquía visual debe mantenerse.

## Accesibilidad

- Utilizar HTML semántico.
- Los inputs deben tener labels.
- Los botones deben ser elementos `button`.
- Los enlaces deben ser elementos `a`.
- Las imágenes deben tener `alt`.
- Mantener estados `focus-visible`.

## Alcance de esta iteración

Implementar exclusivamente:

- estructura de Home
- estilos
- responsive
- componentes necesarios
- routing de Home

NO implementar todavía:

- login
- registro
- JWT
- llamadas HTTP
- hoteles reales del backend
- reservas
- panel admin
- panel superadmin

## Criterios de aceptación

1. `/` muestra la Home de Ica Stay.
2. Ya no aparece contenido predeterminado de Angular.
3. La identidad visual utiliza el sistema definido en `styles.css`.
4. Existe Header, Hero, buscador, alojamientos destacados, Explorar Ica y Footer.
5. La composición no depende exclusivamente de cards repetitivas.
6. Funciona en desktop y móvil.
7. No se agregan frameworks CSS innecesarios.
8. `ng build` finaliza correctamente.
9. No se modifica el backend.

## Iteración visual 2

La estructura visual aprobada en la primera iteración debe conservarse.

No rediseñar el Hero, Header ni buscador.

### Fotografías

La Home utilizará imágenes locales desde:

`frontend/public/images/home/`

Rutas previstas:

- `/images/home/hero-ica.webp`
- `/images/home/hotel-vinedos.webp`
- `/images/home/hotel-dunas.webp`
- `/images/home/hotel-ciudad.webp`
- `/images/home/huacachina.webp`
- `/images/home/vinedos.webp`
- `/images/home/paracas.webp`

Las fotografías deben utilizar `object-fit: cover` cuando corresponda.

No aplicar overlays oscuros innecesarios, gradientes artificiales ni filtros intensos sobre las fotografías.

Las imágenes forman parte de la composición editorial y no deben convertirse en cards genéricas.

### Ajustes visuales

Mantener la composición actual.

Se permite ajustar:

- proporciones de imágenes;
- espaciados;
- alineaciones;
- comportamiento responsive;
- contraste de textos;
- estados hover discretos.

No realizar un rediseño general.

### Navegación temporal

Las funcionalidades todavía no implementadas pueden permanecer indicadas como "Próximamente".

No crear páginas falsas para login, reservas o alojamientos en esta iteración.
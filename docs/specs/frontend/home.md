# Ica Stay — Home pública

## Estado
Frontend 2.0 — completo (incremento final 2.0.13). Actualizado: 2026-10-29.

## Contrato vigente para Frontend 2.0

Esta sección sustituye las restricciones visuales y el alcance provisional de
las iteraciones históricas documentadas abajo. Se permite rediseñar Home, Hero,
Header y buscador por incrementos, conservando sus funcionalidades actuales.

Concepto: **Desert Luxury + Travel Modern**, editorial, cálido y sobrio.
Paleta centralizada en `frontend/src/styles.css`:

| Token base | Color | Uso |
|---|---|---|
| `--ica-green` | `#103F35` | Acciones, foco y énfasis |
| `--ica-gold` | `#B88950` | Acentos y separadores |
| `--ica-arena` | `#EAD8BE` | Superficies cálidas |
| `--ica-cream` | `#FAF7F2` | Fondo |
| `--ica-text` | `#26342F` | Texto principal |

### Alcance 2.0.1

Preparar tokens y actualizar esta spec sin cambiar composición, tipografías,
responsive ni clases existentes. Los alias `--ica-ink`, `--ica-sand`,
`--ica-paper`, `--ica-clay`, `--ica-clay-dark`, `--ica-olive`,
`--ica-olive-light`, `--ica-muted` y `--ica-line` se adaptan dentro de
`app-home`. Header, buscador y footer heredan ese tema solamente en Home.
El host declara fondo y texto propios, ya que sus variables no se propagan a body.
Las demás pantallas y el chatbot montado en App conservan su apariencia actual.
Los tonos auxiliares de hover y texto secundario mantienen contraste; el dorado
no se usa para texto pequeño ni botones con texto blanco sobre fondos claros.
No cambiar backend, rutas, servicios ni dependencias. No crear commits.

### Funcionalidades que deben conservarse

- `/` muestra Home con las anclas `contenido`, `alojamientos` y `explorar`.
- Header: inicio de sesión anónimo, reservas para USER, panel según rol
  administrativo, sesión visible, cierre de sesión y salto al contenido.
- Buscador real por nombre (máximo 180 caracteres) y ciudad (máximo 120):
  recorta espacios, omite filtros vacíos y navega a `/hotels` con `name`, `city`
  y `page=0`. Mantener validación y labels. Fechas/huéspedes no forman parte
  de este buscador; conservar el aviso. Ver [catálogo](user-hotel-catalog.md).
- Hoteles reales desde la API, hasta tres destacados y enlaces al catálogo y
  detalle. Conservar carga, vacío y error, y aviso de fotografías referenciales.
- Huacachina, Viñedos y Paracas siguen siendo contenido editorial con aviso
  de funcionalidad futura. No inventar disponibilidad, precios ni testimonios.
- Footer con anclas, información del proyecto y contacto aún pendiente.
- Chatbot en App: conservar habilitación por rutas públicas, historial,
  teclado, foco, estados y cancelación. Ver [contrato del chat](ai-chat.md).
- Mantener HTML semántico, alt, focus-visible, movimiento reducido y adaptación
  móvil/tablet/escritorio sin scroll horizontal.

### Aceptación 2.0.1

1. Paleta nueva aplicada únicamente al Home mediante tokens compatibles.
2. Sin cambios funcionales ni nuevas dependencias.
3. Suites relacionadas de App/Home, catálogo, autenticación y chatbot, y build.
4. Revisión de contraste y posterior comprobación visual en navegador de Home
   y pantallas compartidas; las pruebas DOM/HTTP no validan apariencia renderizada.

### Incremento 2.0.2 — Header y footer

Home activa explícitamente la variante `home` de SiteHeader y SiteFooter.
Las otras páginas conservan su presentación anterior. Header con logotipo
tipográfico IcaStay, navegación horizontal y sesión según rol. Hasta 1000px,
un botón con `aria-expanded` y `aria-controls` despliega navegación y sesión;
el panel cerrado queda fuera del recorrido de teclado mediante `display:none`.
Escape cierra y devuelve el foco al botón; seleccionar un enlace o cerrar sesión
cierra el panel. Es una navegación desplegable no modal, sin atrapar el foco.
Footer verde oscuro, texto crema/arena, foco visible y distribución vertical
en móvil. Se conservan todas las rutas, anclas y contratos de autenticación.

Validación: pruebas afectadas de navegación/roles, menú, footer, regresión de
App/Home, catálogo y autenticación, build y `git diff --check`. La revisión
visual en navegador se informa por separado de las pruebas DOM.

### Incremento 2.0.3 — Hero y buscador

Hero panorámico con `/images/home/hero-ica.webp`, título editorial claro y
degradado verde para contraste. Este degradado funcional sustituye la prohibición
histórica de overlays para el hero. La imagen actual mide 280 × 180 px; su
nitidez panorámica queda limitada hasta disponer de una fuente de mayor resolución.
Sin mockup adjunto, la composición se basa en la descripción del incremento.

SearchBar se integra en el borde inferior del hero, en flujo normal con un
solapamiento limitado. Se activa una variante `home` sin cambiar su formulario:
solo nombre y ciudad, límites 180/120, normalización, filtros opcionales y
navegación a `/hotels` con `page=0`. No añadir fechas ni huéspedes.
Mantener labels, aviso de disponibilidad futura, envío nativo por teclado,
validación, foco visible y estados del catálogo. El diseño permite crecimiento
del texto sin alturas fijas y reorganiza el buscador en tablet/móvil.

Cambios visuales limitados al Home; estilos globales y otros consumidores de
SearchBar conservan su apariencia. Validar App/Home, búsqueda/catálogo y
componentes compartidos, build y diff. Las pruebas DOM no sustituyen revisión
visual responsive ni comprobación con el mockup original cuando esté disponible.

### Incrementos 2.0.7–2.0.11 — Pantallas restantes (2026-10-29)

Solo presentación: sin cambios de contratos backend, rutas, guards, validaciones
ni reglas de reserva. Galerías y catálogo (2.0.5.5–2.0.6) en
`docs/features/photo-galleries.md`.

- **UI kit compartido** en `styles.css`: `.page-hero`, `.panel`, `.form-grid`,
  `.alert-*`, `.badge-*` (incluye estados de reserva y ACTIVE/INACTIVE),
  `.summary-list`, `.price`, `.data-table.stack` (tabla → tarjetas con
  `data-label` bajo 720 px), `.pager`, `.empty-state`, `.item-card`, `.btn-sm`,
  `.visually-hidden`. Se reutilizan tokens existentes.
- **2.0.7** Detalle editorial: encabezado, PhotoCarousel principal, descripción y
  panel de datos con enlace a disponibilidad. Formulario de fechas/huéspedes en
  rejilla; tarjetas de habitación con carrusel, capacidad y precio real por noche.
- **2.0.8** Reserva: resumen (hotel, habitación, estancia, noches, precio por noche
  y total estimado; tras crear, importe real, PENDING_PAYMENT y retención UTC).
  Historial en tarjetas con badge de estado y detalle en panel. El pago simulado
  existente no se modifica; no se añaden pasarelas.
- **2.0.9** Login/registro: tarjeta sobre fondo arena, errores destacados,
  botón a ancho completo. Registro sigue redirigiendo al login.
- **2.0.10** HOTEL_ADMIN: barra lateral verde, resumen con accesos reales a
  Mi hotel/Habitaciones/Reservas (se retiran las métricas «Próximamente»; no hay
  endpoint de métricas), perfil y formularios en paneles, habitaciones en
  tarjetas con badge de estado, reservas en tabla responsive.
- **2.0.11** SUPER_ADMIN: `SuperadminNav` (Resumen, Hoteles, Usuarios: solo rutas
  existentes) y `superadmin-shell.css` comunes a las seis pantallas; métricas
  del dashboard como tarjetas con los mismos datos del endpoint.

Validación: 464 pruebas Angular aprobadas, `ng build` correcto,
`git diff --check` sin errores.

### Incrementos 2.0.12–2.0.13 — Chatbot IA 2.0 y QA final (2026-10-29)

**2.0.12 Chatbot.** Solo presentación y accesibilidad; mismo `POST /api/v1/ai/chat`,
historial, CSRF, persistencia y prompts. Encabezado verde con aviso «Respuestas
generadas por IA», burbujas diferenciadas, tarjetas de catálogo con portada de
color y precio destacado, sugerencias y reintento como chips. Indicador de
escritura (`aria-hidden`) solo mientras hay una petición real pendiente; el
anuncio sigue en la región `aria-live` existente. Errores con `role="alert"`.
Panel `role="dialog"` no modal con `aria-labelledby/-describedby`; Escape cierra
y devuelve el foco al lanzador. En ≤480 px el panel ocupa toda la pantalla, el
lanzador se oculta mientras está abierto (el botón del encabezado cierra), es
compacto y redondo cuando está cerrado, y Tab circula dentro del panel.
`prefers-reduced-motion` desactiva animaciones. Estilos divididos en
`ai-chat.css` y `ai-chat-messages.css` para respetar el presupuesto de 4 kB.

**2.0.13 QA.** Auditoría automática con Chrome headless (CDP) contra el servidor
de desarrollo y backend reales, a 360/390/768/1280/1440 px: Home, catálogo,
detalle, disponibilidad con habitaciones reales y resumen de reserva, login,
registro, chatbot abierto y guard de `/admin/hotel` (redirige a login). Sin
desbordamiento horizontal, inputs sin label ni imágenes sin `alt`; un `h1` por
vista. Conversación real con IcaStay AI: respuesta con 3 tarjetas, sin errores.
Correcciones: resumen de reserva apilado en móvil, paneles anidados compactos,
galería del detalle 21:9 en escritorio (`--carousel-ratio`), lanzador compacto
en móvil, contraste AA en degradados arena (texto auxiliar #5a554e 5,3:1,
eyebrow 5,6:1, acento del login 3,9:1 en texto grande).

Validación final: 467 pruebas Angular aprobadas, `ng build` sin errores ni
advertencias, `git diff --check` sin errores.

**Verificación manual pendiente:** pantallas autenticadas (USER: reserva, pago
simulado, historial; HOTEL_ADMIN; SUPER_ADMIN) en navegador real, ya que la
auditoría solo usó sesión anónima; lectores de pantalla (NVDA/VoiceOver);
teclado virtual en móviles físicos con el chatbot; galerías con fotos reales
(los hoteles auditados no tenían fotografías y mostraron la imagen referencial).

**Limitaciones conocidas:** el chatbot en escritorio es un diálogo no modal
(Tab puede salir del panel por diseño); el lanzador flotante puede cubrir
parcialmente contenido inferior al final de la página; el botón primario global
conserva el tono arcilla existente fuera de la Home.

## Referencia histórica: iteraciones anteriores

Los requisitos funcionales anteriores evolucionaron al contrato vigente descrito
arriba. Las prohibiciones de rediseño y el uso de datos/buscador temporales de
esta referencia no aplican a Frontend 2.0.

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

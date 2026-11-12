# IcaStay 2.0.5 — Galerías de hoteles y habitaciones

**Estado:** en revisión

**Responsable:** por asignar

**Actualizado:** 2026-10-29

## Incrementos 2.0.5.5, 2.0.5.6 y 2.0.6 — Carruseles, galerías inactivas y catálogo

**2.0.5.6 (backend).** Nuevos GET administrativos, solo HOTEL_ADMIN (ya cubiertos
por SecurityConfig) y propietario resuelto desde JWT, sin filtrar por estado:

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/api/v1/admin/hotel/photos` | Array PhotoView del hotel propio |
| GET | `/api/v1/admin/hotel/rooms/{roomId}/photos` | Array PhotoView de una habitación propia |
| GET | `/api/v1/admin/hotel/photos/{photoId}/content` | Bytes de la foto |
| GET | `/api/v1/admin/hotel/rooms/{roomId}/photos/{photoId}/content` | Bytes de la foto |

Mismo DTO; `url` apunta a la ruta administrativa de contenido. Cabeceras iguales
a las públicas (`no-store`, `nosniff`, `inline`). Habitación/foto ajena o inexistente:
404; JWT ausente/inválido: 401; otro rol: 403. Las rutas públicas siguen exigiendo
hotel y habitación ACTIVE. Sin migraciones nuevas (V3/V4 intactas). Las respuestas
de mutación conservan sus URLs públicas.

**Gestor Angular.** Lee con el GET administrativo (ya no resuelve el hotel con
`GET /admin/hotel`) y descarga cada imagen como blob mediante HttpClient y el
interceptor JWT; los object URLs se revocan al recargar, cambiar recurso o destruir.
Una imagen fallida deja un marcador sin bloquear la gestión.

**2.0.5.5 (público).** `PhotoCarousel` standalone (detalle del hotel y cada
habitación disponible): flechas, indicadores con `aria-current`, teclado
(←/→/Inicio/Fin), contador `aria-live`, diapositivas ocultas con `inert`, primera
imagen eager y resto lazy, sin autoplay y con `prefers-reduced-motion`.
`HotelCover` muestra la portada real en Home y catálogo; si no hay galería pública
o falla, usa la imagen referencial con la etiqueta «Imagen referencial». Las
habitaciones sin fotos no muestran imagen. `PhotoGalleryService.publicList`
comparte una lectura por hotel/habitación durante 60 s (errores no se cachean), de
modo que catálogo → detalle no repite la consulta.

**2.0.6 (catálogo).** Cabecera Desert Luxury, panel de filtros, chips de filtros
aplicados, rejilla responsive de tarjetas (portada, ciudad, nombre, dirección y
descripción), esqueletos de carga, avisos de error/vacío y paginación. Se conservan
parámetros URL, navegación, retorno desde detalle y textos de estado. No se
muestran precios, servicios ni valoraciones.

**Limitación:** el catálogo hace una lectura de galería por hotel visible (máx. 20
por página). Un campo de portada en el listado reduciría esas consultas, pero
cambia el contrato de `HotelView`; queda como mejora futura.

**Validación (2026-10-29):** `mvn -o test` 380 pruebas, 0 fallos (12 omitidas
preexistentes), incluida PhotoHttpIntegrationTest con 24 casos (nuevo caso
parametrizado hotel/habitación inactivos: lectura y contenido del propietario,
orden/portada/borrado persistidos, 401/403/404, aislamiento y públicos ocultos).
Angular: 460 pruebas aprobadas y `ng build` correcto.

## Incremento 2.0.5.4 — Gestor Angular

PhotoGalleryManager standalone reutilizable, integrado en el perfil del hotel y
en el detalle de habitaciones guardadas. La consulta se inicia al pulsar
«Gestionar fotografías»; para habitaciones se resuelve el hotel propio mediante
GET administrativo existente. No se cambian backend, DTOs ni contratos.

- PhotoGalleryService consume las rutas reales de 2.0.5.2/3 mediante HttpClient
  y el interceptor JWT existente. Las imágenes usan API_BASE_URL y el path del DTO.
- Selección múltiple con previews locales revocadas al reemplazar selección,
  cambiar recurso o destruir componente. Un POST multipart por archivo, en serie;
  estado individual y porcentaje cuando el transporte informa progreso.
- Prevalidación de MIME declarado, tamaño y cupo 15/10; el backend conserva la
  validación autoritativa de contenido y dimensiones. Se omiten duplicados dentro
  de una selección. No hay reintentos automáticos de subidas: ante error se detiene
  la cola y se consulta el servidor antes de permitir una nueva selección.
- Una operación activa por gestor; controles bloqueados durante carga/mutación.
  Reordenación por botones nativos con nombres accesibles y lista completa de IDs.
  Borrado con confirmación explícita; portada/orden se vuelven a leer del servidor.
  Conflictos 404/409 recargan la galería sin aplicar cambios optimistas.
- Estados de carga, vacío real, errores, límite y foco visible; estilos encapsulados
  con tokens Desert Luxury. Cambiar habitación cancela consultas pendientes y
  descarta el estado anterior; una mutación ya recibida puede terminar en servidor.

**Limitación del contrato existente (resuelta en 2.0.5.6):** no existía GET administrativo de fotografías.
GET público de metadatos y contenido exige hotel y habitación activos, también
para HOTEL_ADMIN. Un 404 no se interpreta como galería vacía: el gestor informa
la limitación y bloquea mutaciones hasta poder consultar. Administrar galerías
inactivas requiere una ampliación backend futura, fuera del alcance autorizado.
No activar recursos automáticamente ni usar PUT /order como consulta.

## Incremento 2.0.5.3 — Gestión de galerías

Este incremento amplía 2.0.5.2 sin cambiar DTOs ni endpoints públicos existentes.
Angular sigue fuera de alcance. Se conservan V1–V3 y se añade V4 para limpieza durable.

### Endpoints administrativos nuevos

Bases: `/api/v1/admin/hotel/photos` y
`/api/v1/admin/hotel/rooms/{roomId}/photos`.

| Método y sufijo | Entrada | Respuesta |
|---|---|---|
| DELETE `/{photoId}` | Sin cuerpo | 204: metadatos eliminados; limpieza de archivo confirmada o pendiente durable |
| PUT `/order` | `{"photoIds":["UUID", "UUID"]}` | 200: array PhotoView en nuevo orden |
| PUT `/{photoId}/cover` | Sin cuerpo | 200: PhotoView de la portada elegida |

Requieren JWT y HOTEL_ADMIN, sin excepción para SUPER_ADMIN. Cada operación
resuelve el hotel del usuario y verifica habitación y fotografías dentro de ese
propietario; no acepta propietario desde el cuerpo. Recursos inactivos siguen
siendo administrables. UUID ajeno/inexistente: 404, sin revelar su propietario.
JWT ausente/inválido: 401; otro rol: 403; UUID/JSON inválido: 400.

### Orden, portada y concurrencia

- Reordenar requiere la lista completa actual, sin nulos ni duplicados. Lista
  malformada o mayor que el cupo: 400; IDs que no pertenecen a esa galería: 404;
  lista incompleta: 409. `[]` solo es válido para galería vacía. No hay cambios parciales.
- Toda mutación, incluidas subidas, deja posiciones consecutivas `0..n-1`.
  Orden determinista `position ASC,id ASC`; no se cambian IDs, claves ni created_at.
- Cambiar portada no cambia el orden; repetir la elección es idempotente.
  Reordenar conserva la portada. Si hay fotos sin portada por datos antiguos,
  la mutación elige la primera del orden resultante.
- Eliminar portada promueve la primera foto restante según el orden vigente
  (no la primera subida). Eliminar otra foto conserva la portada. Una galería
  vacía no tiene portada. Repetir DELETE de una foto eliminada devuelve 404.
- Hotel y después habitación se bloquean en el mismo orden que en 2.0.5.2;
  borrar, ordenar, elegir portada y subir se serializan para ese propietario.
  Reordenaciones usan unicidad diferida de posición; portada anterior se desmarca
  antes de marcar la nueva, dentro de la misma transacción.
- Sin versionado optimista: órdenes concurrentes con el mismo conjunto son
  last-write-wins. Si hubo una subida, la lista incompleta devuelve 409; si hubo
  un borrado, el ID ya ausente devuelve 404. El cliente deberá recargar la galería.

### Consistencia y limpieza durable

V4 crea `photo_file_deletions(storage_key PK, created_at TIMESTAMPTZ, last_attempt_at TIMESTAMPTZ)`
e índice de reintento. DELETE elimina metadatos, compacta posiciones, promueve portada
y registra la clave para borrar **en la misma transacción**. Un rollback conserva
fila, orden, portada y archivo, sin publicar ninguna tarea de limpieza.

Después del commit se intenta limpiar mediante PhotoDeletionCleanup en una
transacción independiente. Un fallo de disco deja la tarea persistida; no revierte
la eliminación lógica ni devuelve un 500 engañoso. No se sirve el archivo ya
desvinculado porque la lectura pública exige sus metadatos.

Un proceso programado reintenta hasta 20 tareas por pasada cada 60000 ms, con
`icastay.photos.cleanup-delay` configurable. Prioriza tareas sin intentar y luego
las de intento más antiguo para que fallos persistentes no bloqueen las nuevas.
Usa `FOR UPDATE SKIP LOCKED` para
evitar que varios trabajadores procesen la misma clave simultáneamente. El
borrado físico es idempotente: si una caída ocurre después de borrar y antes de
confirmar la tarea, el siguiente intento completa la limpieza aunque el archivo
ya no exista. Errores se registran sin exponer rutas al cliente.

Las claves UUID no se reutilizan. El almacenamiento debe seguir siendo local a
una instancia o compartido por todas las réplicas que consuman esa cola.
Una interrupción brusca durante una **subida** todavía puede dejar un huérfano
sin metadatos ni tarea; su reconciliación general permanece fuera de esta etapa.

### Validación 2.0.5.3 (2026-10-29)

50 pruebas aprobadas, cero fallos, errores u omisiones: PhotoValidatorTest,
LocalPhotoStorageTest, PhotoGalleryMigrationTest, PhotoHttpIntegrationTest,
HotelServiceTest, RoomServiceTest y JwtAuthenticationFilterTest. Incluyen 22
casos HTTP sobre PostgreSQL aislado, JWT/roles, aislamiento, orden, portada,
eliminación, rollback, competencia entre mutaciones y limpieza durable reintentada
desde una nueva instancia. Los tests de V3 fijan target 3; la suite HTTP migra
hasta V4 sin modificar el esquema de trabajo.

`mvn.cmd -o compile`: BUILD SUCCESS. `git diff --check`, incluidos archivos nuevos:
sin errores. No se instalaron dependencias ni se modificó Angular. Queda aplicar
V4 al entorno de ejecución cuando se despliegue este incremento; las pruebas
no representan un reinicio completo del servidor ni una integración S3.

## Referencia del incremento 2.0.5.2 — Almacenamiento local y subida

Implementa subida y lectura sobre V3 sin modificar migraciones ni DTOs anteriores
de hotel/habitación. El alcance histórico 2.0.5.1 se conserva debajo. Eliminación,
reordenación, selección manual de portada y frontend siguen pendientes.

### Endpoints

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/api/v1/admin/hotel/photos` | HOTEL_ADMIN del hotel resuelto desde JWT |
| POST | `/api/v1/admin/hotel/rooms/{roomId}/photos` | HOTEL_ADMIN, habitación propia |
| GET | `/api/v1/hotels/{hotelId}/photos` | Público, hotel ACTIVE |
| GET | `/api/v1/hotels/{hotelId}/rooms/{roomId}/photos` | Público, hotel y habitación ACTIVE y relacionados |
| GET | `/api/v1/hotels/{hotelId}/photos/{photoId}/content` | Misma visibilidad, foto del hotel indicado |
| GET | `/api/v1/hotels/{hotelId}/rooms/{roomId}/photos/{photoId}/content` | Misma visibilidad, foto de la habitación indicada |

Subida `multipart/form-data`: **una fotografía por petición**, campo `file`
obligatorio y `altText` opcional (hasta 500 caracteres, espacios exteriores
recortados). Varias fotos se suben mediante peticiones independientes; no existe
atomicidad de un lote. No enviar un `Content-Type` JSON ni fabricar el boundary.
La identidad/propiedad nunca procede de parámetros hotelId/adminUserId del cliente.
Se permite administrar propietarios inactivos; sus imágenes no se sirven públicamente.

POST devuelve 201 y Location de lectura de contenido. DTO nuevo `PhotoView`:
`id`, `url`, `mimeType`, `sizeBytes`, `width`, `height`, `altText`, `position`,
`isCover`, `createdAt`. La URL es relativa al origen del backend, no al origen
Angular. No expone `storage_key`, ruta local ni nombres originales. GET de
metadatos devuelve array ordenado por `position ASC, id ASC`; galería vacía: `[]`.
Contenido usa MIME detectado, `Content-Disposition: inline`, `nosniff` y
`Cache-Control: no-store`. También se deshabilita caché en metadatos para no
reutilizar respuestas después de desactivar propietarios. Un archivo ausente
devuelve 404; no se elimina automáticamente su registro.

Errores: 400 (archivo vacío/corrupto, formato o dimensiones inválidas, alt largo),
401 (JWT ausente/inválido), 403 (rol incorrecto), 404 (propietario/foto ausente,
ajeno o no visible), 409 (cupo agotado), 413 (tamaño), 503 (almacenamiento o
decodificador indisponible/saturado). Fallos inesperados de persistencia: 500,
con rollback y compensación del archivo. Un administrador sin hotel recibe 404.

### Validación real y límites

- Límite de archivo de 5 MiB (5 242 880 bytes), petición multipart de 6 MiB.
- Detección por bytes y decodificación completa JPG/PNG con ImageIO del JDK.
  MIME y nombre original no se consideran evidencia del formato.
- WebP: RIFF/chunks/dimensiones validados y decodificación real con el FFmpeg
  **ya instalado**, mediante ProcessBuilder sin shell y formato webp_pipe fijo.
  No se instalan dependencias Maven ni ejecutables. `PHOTO_WEBP_DECODER` permite
  indicar la ruta al ejecutable. Sin ese ejecutable, WebP devuelve 503; JPG/PNG
  siguen disponibles. WebP animado se rechaza en este incremento.
- Máximo 8192 píxeles por lado y 16 000 000 píxeles totales antes de decodificar.
  Dos validaciones simultáneas por proceso; WebP tiene timeout de 10 segundos,
  protocolo file/pipe restringido y temporales eliminados al terminar.
- Cupos 15/10, asignación de la primera posición libre y portada solo para la
  primera foto. Bloqueo transaccional de hotel y después habitación, con segunda
  comprobación del cupo y pertenencia. Decodificación/escritura fuera del bloqueo.

### Persistencia y despliegue

`PhotoStorage` define store/read/delete (delete es solo compensación interna).
`LocalPhotoStorage` usa claves `hotels/<UUID>/<UUID>` o `rooms/<UUID>/<UUID>`,
CREATE_NEW sin sobrescritura, rechazo de traversal/enlaces simbólicos y ruta
normalizada bajo una raíz configurable. El volumen solo debe ser escribible
por la cuenta del backend; no ofrece protección contra un operador local que
altere simultáneamente los directorios.

Configurar y exportar `PHOTO_STORAGE_ROOT` como directorio persistente absoluto
**fuera del repositorio**. Defecto: `${user.home}/.icastay/photos`. `.env.example`
documenta las variables, pero Spring ejecutado desde el host no carga ese archivo
automáticamente. El directorio se crea al guardar; permisos/espacio insuficientes
devuelven 503. Guardar junto a los backups de PostgreSQL un backup de esa raíz.

Compose actualmente solo ejecuta PostgreSQL y el backend corre en el host: no
se añade un volumen Docker que el backend no utilizaría. Si se containeriza el
backend, montar un volumen persistente en PHOTO_STORAGE_ROOT. En varias réplicas
se necesitará almacenamiento compartido o un adaptador S3 (aún no implementado).

Si falla escritura local, se intenta quitar el archivo parcial. Si falla la
transacción de metadatos, una sincronización de rollback elimina el objeto.
Si no llega a iniciarse la transacción, también se compensa. Un resultado de
commit desconocido conserva el archivo para evitar romper una fila confirmada.
Fallo de compensación registra la clave para reconciliar; una caída abrupta entre
archivo y commit puede dejar huérfanos. Reconciliación durable y recuperación
automática de esos casos quedan pendientes; no hay atomicidad filesystem/BD.

### Pruebas 2.0.5.2

Suites `PhotoValidatorTest`, `LocalPhotoStorageTest`, `PhotoHttpIntegrationTest`
y la previa `PhotoGalleryMigrationTest`. HTTP real con JWT y PostgreSQL sobre
esquema aislado, almacenamiento temporal fuera del repositorio y limpieza al
finalizar. Incluyen decodificación WebP real, formatos inválidos, límites,
roles/propiedad, visibilidad, cupos, concurrencia, compensación y lectura desde
una nueva instancia de almacenamiento. No se afirma un reinicio integral del
servidor ni integración S3. Las pruebas requieren el FFmpeg existente para WebP.

Validación final (2026-10-29): 37 pruebas aprobadas, cero fallos, errores u
omisiones en las cuatro suites de galerías y las regresiones HotelServiceTest,
RoomServiceTest y JwtAuthenticationFilterTest. Los nueve casos HTTP incluyen
subidas concurrentes iniciales, competencia por el último cupo y archivo ausente.
`mvn.cmd -o compile`: BUILD SUCCESS. `git diff --check` y comprobación de archivos
nuevos: sin errores. Se conservaron los cambios existentes, sin correcciones
adicionales de código en esta revisión.

`mvn.cmd -o -DskipTests package` no pudo completar el JAR: faltan dependencias
del plugin maven-jar-plugin 3.5.1 en la caché local. No se descargaron ni instalaron
dependencias. Pendiente empaquetar cuando esa caché esté disponible; la compilación
y las pruebas sí finalizaron correctamente.

## Referencia del incremento 2.0.5.1

## Objetivo

Preparar metadatos persistentes de fotografías de hoteles y habitaciones.
Esta etapa incorpora únicamente spec, migración Flyway y pruebas PostgreSQL.
Los flujos descritos a continuación son requisitos para incrementos posteriores,
no APIs ni almacenamiento ya implementados.

## Actores y permisos

| Actor | Operación futura | Alcance |
|---|---|---|
| HOTEL_ADMIN | Subir, eliminar, ordenar y elegir portada | Su hotel y sus habitaciones |
| Público/USER | Consultar fotografías | Solo hoteles y habitaciones ACTIVE |

Resolver propietario desde la identidad autenticada y comprobar pertenencia en
backend para cada foto, incluida cada entrada de una reordenación. Mantener
administración de recursos inactivos y las restricciones actuales de rol;
SUPER_ADMIN no obtiene permisos de gestión fotográfica implícitos.

## Flujo principal

1. Guardar primero el hotel o habitación: las fotos requieren un propietario existente.
2. Validar archivo y cupo; almacenar el archivo fuera de PostgreSQL y registrar metadatos.
3. La primera fotografía será portada. Las siguientes conservan la portada actual.
4. Ordenar o cambiar portada mediante una operación atómica por galería.
5. Eliminar la foto; si era portada, elegir la primera restante según
   `position ASC, id ASC`. Una galería vacía no tiene portada.

No mover automáticamente la portada al principio: selección y orden son
independientes. Los consumidores usan la portada explícita y muestran las fotos
en `position ASC, id ASC`. Si faltara portada en datos incompletos, usar como
fallback la primera de ese orden, sin escribir durante una lectura.

## Datos y contratos

Nueva migración `V3__photo_galleries.sql`; V1 y V2 permanecen intactas.
Tablas separadas `hotel_photos` y `room_photos` con propietarios obligatorios;
no introducir propietario polimórfico ni bytes/Base64 en la BD.

| Campo | Regla |
|---|---|
| id | UUID PK, generado por aplicación, como las entidades existentes |
| hotel_id / room_id | UUID FK no nulo al propietario; sin borrado en cascada |
| storage_key | VARCHAR(512), no vacío, sin espacios exteriores, único en su tabla |
| mime_type | image/jpeg (JPG/JPEG), image/png o image/webp |
| size_bytes | BIGINT, entre 1 y 5 242 880 bytes inclusive |
| width / height | INTEGER positivos, dimensiones reales en píxeles |
| alt_text | VARCHAR(500), no nulo; vacío por defecto, usar descripción contextual como fallback |
| position | INTEGER, base cero: hotel 0–14, habitación 0–9 |
| is_cover | BOOLEAN no nulo, false por defecto |
| created_at | TIMESTAMPTZ no nulo, CURRENT_TIMESTAMP por defecto |

El límite operativo «5 MB» se define como 5 × 1024 × 1024 bytes (5 MiB).
El tamaño se refiere al archivo guardado; el futuro upload también debe rechazar
entradas que excedan ese límite antes de cualquier transformación.
La BD valida metadatos, no demuestra que los bytes sean una imagen válida.

### Cupo, orden y concurrencia

- Máximo 15 fotos/hotel y 10/habitación. El rango de posición más su unicidad por
  propietario garantiza el cupo al confirmar, sin triggers ni contadores.
- Se permiten huecos tras eliminar; reutilizar una posición libre. El servicio
  podrá compactar/reordenar dentro del rango. No usar siempre `max(position)+1`.
- La unicidad de posición es `DEFERRABLE INITIALLY IMMEDIATE`: para intercambiar
  posiciones, diferir explícitamente esa restricción dentro de la transacción,
  realizar todos los cambios y validarla antes del commit. Sin duplicados finales.
- Índices de orden `(propietario, position, id)` e índices únicos parciales
  `(propietario) WHERE is_cover` impiden dos portadas incluso concurrentes.
- La BD garantiza **como máximo una portada**, no exactamente una. Promover una
  nueva al borrar y elegir la primera al subir son obligaciones del futuro servicio.
  Cambio de portada: desmarcar la anterior antes de marcar la nueva, atómicamente.
- Serializar futuras mutaciones de una galería mediante bloqueo del propietario,
  respetando el orden de bloqueos de hotel/habitación existente. No mantener esos
  bloqueos durante transferencia o procesamiento de archivos.
- Las FK impiden borrar físicamente un propietario con fotos. El estado INACTIVE
  no borra fotos. No usar cascadas que oculten archivos pendientes de limpiar.

### Preparación del almacenamiento

`storage_key` es una clave relativa opaca, nunca ruta absoluta, URL ni URL firmada.
Reservar namespaces separados `hotels/<hotelUUID>/<photoUUID>` y
`rooms/<roomUUID>/<photoUUID>` para evitar colisiones entre las tablas.
La futura abstracción de almacenamiento resolverá claves tanto en volumen local
persistente como en S3; la unicidad entre tablas depende de esos namespaces.
No se crean carpetas, buckets, adaptadores, endpoints ni configuración en esta etapa.

## Alternativas y errores

- Rechazar cupo agotado, metadatos fuera de rango, foto ajena, formato inválido
  y orden con IDs ajenos/duplicados. Códigos HTTP pendientes del incremento API.
- Comprobar firma/decodificación del contenido, dimensiones y límites de píxeles
  en el futuro servicio; no confiar en extensión ni MIME enviado por cliente.
- Fallos entre archivo y BD requieren compensación y limpieza reintentable.
  Quitar metadatos y promover portada en una transacción; limpiar el archivo con
  mecanismo durable posterior. No afirmar atomicidad entre PostgreSQL y archivos.
- El índice de portada no ejecuta promoción automática tras un DELETE SQL.

## Estados

Galería vacía o con fotos; portada explícita o fallback de lectura. No se añade
estado de carga al esquema: carga/error/reintento son responsabilidad futura de UI.

## Criterios de aceptación

- Migrar una BD vacía y una V2 con hoteles/habitaciones conserva los datos previos.
- Ambas tablas validan FK, nulabilidad, formato, tamaño, dimensiones, clave y cupo.
- Unicidad de portada por propietario y orden reordenable sin duplicados finales.
- Reejecutar Flyway no aplica migraciones nuevas y valida checksums.
- Pruebas sobre esquema PostgreSQL aislado, sin modificar el esquema de trabajo.

## Fuera de alcance de esta etapa

APIs, entidades JPA nuevas, servicios, procesamiento/subida/borrado de archivos,
almacenamiento local/S3, cambios de autenticación y frontend. No nuevas dependencias.

## Validación ejecutada

`mvn.cmd -Dtest=PhotoGalleryMigrationTest test`: 10 pruebas, cero fallos,
errores u omisiones; BUILD SUCCESS sobre PostgreSQL 17.11. Incluye creación
desde cero, actualización V2 con datos previos, reejecución y validación Flyway,
cupo/posición, FK, metadatos, portada y reordenación diferida para ambas tablas.
Los fixtures usan esquemas aleatorios `photo_test_<UUID>` eliminados al terminar,
con rollback por caso. El esquema de trabajo no se migró.

La suite utiliza `DB_URL`, `DB_USER` y `DB_PASSWORD` existentes (valores por
defecto de conexión iguales a application.yml); requiere permiso CREATE de esquema.
No levanta Spring, MongoDB ni Ollama. No verifica todavía operaciones API ni
archivos reales, ni ejecuta carreras concurrentes entre conexiones.

## Decisiones y pendientes

El esquema sigue las FK UUID actuales sin forzar asociaciones JPA. Implementar
después permisos, promoción de portada, cuotas por operación, validación binaria,
límite de píxeles, eliminación durable y carruseles accesibles. Los archivos deben
persistir fuera de PostgreSQL; no se ha verificado ninguna integración S3.

Referencias: [arquitectura](../architecture/architecture.md) ·
[gestión hotelera](hotel-management.md) · [catálogo público](../specs/frontend/user-hotel-catalog.md)

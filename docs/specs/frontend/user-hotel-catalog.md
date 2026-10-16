# Incremento 5.1 — Búsqueda y visualización de hoteles

**Estado:** en revisión. **Actualizado:** 2026-10-15.

## Alcance y acceso

Visitantes y usuarios autenticados consultan hoteles activos, sin guard ni JWT
obligatorio. Se reutilizan modelos, interceptor, cabecera, pie y estilos existentes.
No se modifican permisos ni endpoints HOTEL_ADMIN.

## Rutas y contratos

- `/hotels`: catálogo; query parameters `name`, `city`, `page` conservan búsqueda.
- `/hotels/:id`: detalle; conserva esos parámetros para volver al catálogo.
- GET `/api/v1/hotels`: name parcial literal (incluidos % y _), city exacta;
  ambos sin distinguir mayúsculas, combinados con AND. String.strip recorta
  espacios exteriores; vacío equivale a ausente. Máximos 180/120 respectivamente.
  No se garantiza equivalencia entre letras con y sin tilde.
- page desde 0 (defecto 0), size 1–100 (defecto 20). Se rechazan desplazamientos
  page * size mayores que Integer.MAX_VALUE. Angular utiliza 20 elementos.
  Orden fijo name ASC, id ASC. Totales calculados sobre resultados filtrados.
- Respuesta HotelPage: content, page, size, totalElements, totalPages.
- GET `/api/v1/hotels/{id}`: HotelView con id, name, description nullable,
  address, city. Inexistente/inactivo: 404; UUID o parámetros inválidos: 400.

## Interfaz

Aplicar/limpiar reinician página. Paginación usa filtros aplicados, no cambios
pendientes del formulario. URL permite recarga y navegación atrás/adelante.
Página inválida en URL se interpreta como 0; límites de filtros inválidos impiden
consultas y muestran error. Cargas canceladas no sobrescriben resultados nuevos.
Estados: carga, resultados, vacío, conexión/error general y reintento; detalle
tiene mensaje específico 404 y descripción alternativa si es null.

Home conserva tres espacios editoriales, fotografías referenciales y sus secciones.
Incluye acceso al catálogo y enlaces de detalle. El buscador ahora tiene solo nombre
y ciudad, con aviso de que fechas/huéspedes aún no comprueban disponibilidad.
Catálogo y detalle no asignan imágenes arbitrarias a establecimientos.

## Criterios de aceptación

- Búsquedas individuales/combinadas y paginación pública con exclusión de inactivos.
- Normalización de espacios, mayúsculas y vacíos; validación de límites.
- Navegación catálogo → detalle → catálogo conserva búsqueda y página.
- Carga, vacío, errores y reintento; cancelación de consultas obsoletas.
- Formularios etiquetados, foco global visible y layout adaptable sin dependencias.
- Regresión de Home y del panel administrativo cubierta por suites existentes.

## Límites y revisión manual

Sin habitaciones, disponibilidad, reservas, pagos, mapas, imágenes propias ni
migraciones. ACTIVE indica publicación, no disponibilidad para fechas concretas.
No hay selector de ciudades ni orden configurable. Revisar manualmente en móvil
y escritorio, navegación por teclado, búsqueda/retorno y estados contra la API real.
Las pruebas nuevas de persistencia crean fixtures en una transacción con rollback;
las pruebas Angular simulan HTTP. No se añaden seeds ni se alteran datos existentes.

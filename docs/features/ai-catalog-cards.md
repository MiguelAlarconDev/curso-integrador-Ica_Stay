# IA-06.2 — Tarjetas del catálogo público en el chatbot

**Estado:** implementado y validado mediante pruebas automatizadas.
**Actualizado:** 2026-10-22.

## Objetivo y alcance

Mostrar las consultas verificadas de IA-06.1 en tarjetas compactas, enlazar al
detalle público existente `/hotels/:id` y ofrecer preguntas sugeridas editables.
Sin nuevas dependencias, permisos, endpoints, reglas de reservas/pagos ni consultas
adicionales para formar tarjetas. Se conservan cookies, CSRF, JWT e historial.
La modificación local preexistente en `ai-assistant.md` se conserva sin editarla.

## Contrato retrocompatible

POST `/api/v1/ai/chat` sigue recibiendo `{message}` y devolviendo `answer`.
Las consultas de catálogo resueltas añaden un campo opcional `catalog`:

```text
catalog: {
  checkedAt: Instant ISO,
  checkIn: fecha ISO | null, checkOut: fecha ISO | null, guests: entero | null,
  roomsRequested: boolean,
  hotels: [{
    id: UUID público, name: string, city: string,
    availability: UNKNOWN | AVAILABLE | UNAVAILABLE,
    rooms: [{number: string, capacity: entero, pricePerNight: decimal string, currency: string}]
  }]
}
```

El lector de catálogo construye texto y DTO en la misma consulta, a partir de los
mismos campos públicos. No analiza texto de Ollama ni consulta nuevamente PostgreSQL.
Máximo tres hoteles y tres habitaciones por hotel. El precio se transmite como
cadena decimal exacta con moneda, por noche, sin inventar totales ni tarifas mínimas.
No contiene entidades, descripciones, administradores, usuarios ni reservas.
Respuestas generales, aclaraciones y ausencia/ambigüedad de hotel omiten `catalog`.
`source` continúa siendo interno y no se serializa.

`UNKNOWN` significa que no se consultó disponibilidad por fechas. `AVAILABLE`
significa que la consulta devolvió habitaciones para esas fechas/huéspedes.
`UNAVAILABLE` significa que el hotel ACTIVE no devolvió habitaciones para esos
criterios. La consulta no bloquea inventario ni garantiza disponibilidad posterior.
Las tarjetas muestran el momento de consulta y el aviso de que puede cambiar.
Si solo se listaron hoteles, no se inventa precio ni capacidad: se indican no consultados.

## Presentación e historial

- Componente standalone `AiCatalogCards`, estilos con tokens existentes, tarjetas
  verticales y texto que se ajusta al ancho del panel móvil/escritorio.
- Enlaces internos mediante RouterLink y UUID; ninguna URL del modelo ni URL externa.
- Respuesta textual completa disponible en un elemento `details`; solo una región
  aria-live anuncia la respuesta. Tarjetas sin anuncios redundantes, enlaces con
  nombre accesible y foco visible.
- Sugerencias para hoteles, precios y disponibilidad. Rellenan el campo, no envían
  automáticamente. La sugerencia de disponibilidad usa entrada UTC en siete días,
  salida dos días después y dos huéspedes; el usuario puede modificar todo antes
  de enviar. Deshabilitadas durante carga de historial/generación.
- Se conserva validación de 1000 caracteres, Enter/Shift+Enter, foco, cancelación y reintento.
- Angular valida el bloque opcional, sus límites y coherencia entre fechas/estado;
  metadata inválida se descarta y permanece el texto, sin construir tarjetas por inferencia.

MongoDB conserva el esquema y el intercambio textual existente. El DTO de tarjetas
solo vive en la respuesta actual y memoria del componente. Al recuperar historial,
los intercambios aparecen como texto con el aviso «Respuesta guardada: sus datos
no se han vuelto a comprobar». Esto aplica también a las consultas hechas en IA-06.2
después de recargar: no se reconstruyen tarjetas desde texto ni se reconsultan
hoteles. No se presenta disponibilidad antigua como una comprobación nueva.
Persistir snapshots estructurados sería un incremento de esquema separado.

## Validación

- 142 pruebas backend aprobadas, ninguna omitida, incluyendo el contrato JSON,
  proyección pública, precios exactos, estados de disponibilidad y consultas reales
  en PostgreSQL aislado, además de regresión IA-05, filtros hoteleros, solapamiento y JWT.
- 101 pruebas frontend aprobadas en siete archivos: renderizado seguro, navegación
  al detalle, metadata inválida, sugerencias, recuperación sin solicitudes extra,
  estados de disponibilidad y regresión de chatbot/autenticación/rutas públicas.
- `npm.cmd run build`: correcto; bundle inicial 370.41 kB, dentro del presupuesto.
- `mvn.cmd -DskipTests compile` y `git diff --check`: correctos.
- Bases de prueba aisladas en 55439 (PostgreSQL) y 27028 (MongoDB); no se escribieron
  fixtures en bases reales. Los procesos de prueba se detienen al terminar.

Comandos frontend:

```text
npm.cmd test -- --watch=false --include=src/app/features/ai/**/*.spec.ts --include=src/app/core/services/ai-chat.service.spec.ts --include=src/app/core/models/ai-catalog-validation.spec.ts --include=src/app/core/auth/auth.spec.ts --include=src/app/app.spec.ts
npm.cmd run build
```

Backend: la selección documentada en IA-06.1 (`Ai*Test,Ollama*Test,RoomServiceTest,AvailabilityIntegrationTest,HotelSearchIntegrationTest,JwtAuthenticationFilterTest`)
con las variables y datasource explícitos de prueba aislada.
Pendiente revisión visual en navegador móvil/escritorio y prueba integral con los
servicios locales desplegados. Las pruebas UI usan DOM simulado; no sustituyen esa revisión.

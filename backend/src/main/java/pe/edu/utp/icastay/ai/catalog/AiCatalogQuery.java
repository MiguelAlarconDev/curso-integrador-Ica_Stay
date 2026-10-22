package pe.edu.utp.icastay.ai.catalog;

import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Pattern;

/** Deliberately small grammar: no generated SQL, tool names or filters from the model. */
public record AiCatalogQuery(String city, String name, boolean rooms, String checkIn, String checkOut, Integer guests) {
    static final String HELP = "Indica la ciudad (por ejemplo: en Ica) o el nombre completo del hotel. "
            + "Para disponibilidad indica entrada y salida AAAA-MM-DD y número de huéspedes, "
            + "por ejemplo: habitaciones en Ica del 2030-01-02 al 2030-01-04 para 2 huéspedes.";
    public record Plan(Optional<AiCatalogQuery> query, String clarification) {}

    public static Optional<Plan> parse(String message) {
        String text = Normalizer.normalize(message, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
        if (Pattern.compile("\\b(reserva|reservar|reserven|pago|pagar|usuarios?|administrador|sql|password|contrasena|elimina|modifica|crea)\\b")
                .matcher(text).find()) return Optional.of(new Plan(Optional.empty(), "Solo consulto datos públicos de alojamientos. No accedo a datos privados ni realizo cambios. " + HELP));
        if (Pattern.compile("\\b(politica|politicas|mascotas|desayuno|cancelacion|wifi|piscina)\\b").matcher(text).find())
            return Optional.of(new Plan(Optional.empty(), "No dispongo de políticas ni servicios verificados. Consulta directamente al alojamiento."));
        boolean relevant = Pattern.compile("\\b(hotel(?:es)?|habitacion(?:es)?|alojamientos?|precio|precios|tarifa|tarifas|capacidad|disponibilidad|disponibles?|huespedes|personas|ciudad)\\b").matcher(text).find();
        if (!relevant) return Optional.empty();
        var nameMatch = Pattern.compile("(?iu)\\bhotel\\s+(?!(?:en|hay|disponibles?)\\b)(?:[\"“]([^\"”]{1,180})[\"”]|([^\"“”?!.,;\\n]{1,180}?)(?=\\s+(?:del|desde|entre|para|con|en|al)\\b|[?!.,;\\n]|$))").matcher(message);
        String name = null;
        String locationText = message;
        if (nameMatch.find()) {
            name = (nameMatch.group(1) != null ? nameMatch.group(1) : nameMatch.group(2)).strip();
            // Mask the complete mention, including city-like words within the hotel's name.
            locationText = message.substring(0, nameMatch.start()) + ";" + message.substring(nameMatch.end());
            if (name.matches("(?iu)(en|disponible|disponibles|hay)")) name = null;
        }
        var cityMatch = Pattern.compile("(?iu)(?:\\ben\\s+(?:(?:la\\s+)?ciudad\\s+de\\s+)?|\\bciudad\\s*[:=]\\s*)([\\p{L} ]{1,120}?)(?=\\s+(?:para|del|desde|con|entre|el|hotel|habitaciones|capacidad)\\b|[?.,;!\\n]|$)").matcher(locationText);
        String city = cityMatch.find() ? cityMatch.group(1).strip() : null;
        if (city == null && name == null) return Optional.of(new Plan(Optional.empty(), HELP));
        var dates = Pattern.compile("\\b[0-9]{4}-[0-9]{2}-[0-9]{2}\\b").matcher(text).results().map(r -> r.group()).toList();
        var guestCounts = Pattern.compile("(?<![\\w.-])(-?[0-9]+(?:[.,][0-9]+)?)\\s*(?:huesped(?:es)?|personas?|adultos?)\\b")
                .matcher(text).results().map(r -> r.group(1)).toList();
        Integer guests = null;
        if (guestCounts.size() == 1 && guestCounts.getFirst().matches("[0-9]{1,2}")) guests = Integer.valueOf(guestCounts.getFirst());
        boolean availability = !dates.isEmpty() || !guestCounts.isEmpty()
                || Pattern.compile("\\b(disponibilidad|hoy|manana|fechas|entrada|salida)\\b").matcher(text).find();
        if (availability && (dates.size() != 2 || guests == null || guests < 1 || guests > 20))
            return Optional.of(new Plan(Optional.empty(), "Para comprobar disponibilidad faltan o no son válidas las fechas/huéspedes: "
                    + "indica entrada y salida AAAA-MM-DD y entre 1 y 20 huéspedes por habitación."));
        boolean rooms = availability || Pattern.compile("habitacion|precio|tarifa|capacidad").matcher(text).find();
        return Optional.of(new Plan(Optional.of(new AiCatalogQuery(city, name, rooms,
                availability ? dates.get(0) : null, availability ? dates.get(1) : null, guests)), null));
    }
}

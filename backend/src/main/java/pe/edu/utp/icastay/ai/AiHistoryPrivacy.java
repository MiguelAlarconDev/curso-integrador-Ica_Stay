package pe.edu.utp.icastay.ai;

import java.util.regex.Pattern;

/** Conservative exclusion of common secrets/private booking information; never logs inspected text. */
final class AiHistoryPrivacy {
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?iu)(contrase[nñ]a|password|passwd|secreto|secret|token|api.?key|authorization|bearer|"
            + "reserva|reservation|tarjeta|card.number|cvv|dni|pasaporte|passport|correo|email|tel[eé]fono|"
            + "[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}|[a-z0-9_/-]{40,}|(?:\\d[ -]?){8,})");
    private AiHistoryPrivacy() {}
    static boolean mayStore(String question, String answer) {
        return question != null && answer != null && answer.length() <= 16000
                && !SENSITIVE.matcher(withoutDates(question)).find() && !SENSITIVE.matcher(withoutDates(answer)).find();
    }

    private static String withoutDates(String value) {
        return Pattern.compile("\\b[0-9]{4}-[0-9]{2}-[0-9]{2}\\b").matcher(value).replaceAll(match -> {
            try { java.time.LocalDate.parse(match.group()); return "[fecha]"; }
            catch (java.time.DateTimeException invalid) { return match.group(); }
        });
    }
}

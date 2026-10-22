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
                && !SENSITIVE.matcher(question).find() && !SENSITIVE.matcher(answer).find();
    }
}

package pe.edu.utp.icastay.payment;

public enum PaymentResult {
    APPROVED, REJECTED;

    @com.fasterxml.jackson.annotation.JsonCreator
    public static PaymentResult fromJson(String value) {
        return valueOf(value);
    }
}

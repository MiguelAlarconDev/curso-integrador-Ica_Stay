package pe.edu.utp.icastay.payment;

import java.util.UUID;

public record PaymentView(UUID id, PaymentResult result, String reference) { }

package gt.rutagt.clientes.application;

import gt.rutagt.clientes.api.dto.ClienteDtos.DebitoRequest;
import gt.rutagt.clientes.api.dto.ClienteDtos.DebitoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Si dos reintentos del mismo débito llegan a la vez, el segundo choca con la referencia única y su
 * transacción completa (incluido el descuento) se revierte; aquí se devuelve el resultado del primero.
 */
@Component
@RequiredArgsConstructor
public class DebitoIdempotente {

    private final CarteraService carteras;

    public DebitoResponse ejecutar(String idempotencyKey, DebitoRequest req) {
        String referencia = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : req.referencia();
        try {
            return carteras.debitar(req.usuarioId(), req.clienteId(), req.monto(), referencia, req.concepto(), req.fecha());
        } catch (DataIntegrityViolationException e) {
            return carteras.consultarDebito(referencia);
        }
    }
}

package gt.rutagt.viajes.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Fachada pública del módulo de ventas para los consumidores de eventos. */
@Component
@RequiredArgsConstructor
public class VentasEventos {

    private final VentaTx tx;

    public void viajeEliminado(Eventos.ViajeEliminadoRef e) {
        tx.limpiarViajeEliminado(e.viajeId());
    }
}

package gt.rutagt.flota.infrastructure.client;

import gt.rutagt.common.errors.ApiException;
import gt.rutagt.common.errors.ServicioNoDisponibleException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Envoltura resiliente de {@link ViajesClient}: reintentos ante fallos de red y circuit breaker.
 * Si ms-viajes no responde, se falla de forma SEGURA (503): no se desactiva ni se elimina nada a ciegas.
 */
@Component
@RequiredArgsConstructor
public class ViajesGateway {

    private final ViajesClient client;

    @CircuitBreaker(name = "ms-viajes", fallbackMethod = "sinServicioConteo")
    @Retry(name = "ms-viajes")
    public long viajesActivosDeBus(UUID busId) {
        return client.activos(busId, null).cantidad();
    }

    @CircuitBreaker(name = "ms-viajes", fallbackMethod = "sinServicioConteo")
    @Retry(name = "ms-viajes")
    public long viajesActivosDeChofer(UUID choferId) {
        return client.activos(null, choferId).cantidad();
    }

    @CircuitBreaker(name = "ms-viajes", fallbackMethod = "sinServicioExiste")
    @Retry(name = "ms-viajes")
    public boolean rutaTieneViajes(UUID rutaId) {
        return client.existePorRuta(rutaId).existe();
    }

    @SuppressWarnings("unused")
    private long sinServicioConteo(UUID id, Throwable t) {
        throw falla(t);
    }

    @SuppressWarnings("unused")
    private boolean sinServicioExiste(UUID id, Throwable t) {
        throw falla(t);
    }

    private static RuntimeException falla(Throwable t) {
        if (t instanceof ApiException api) {
            return api;
        }
        return new ServicioNoDisponibleException(
                "No se pudo verificar los viajes en este momento (servicio de viajes no disponible). Intenta más tarde.");
    }
}

package gt.rutagt.viajes.infrastructure.client;

import gt.rutagt.common.errors.ApiException;
import gt.rutagt.common.errors.ServicioNoDisponibleException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Envoltura resiliente (timeout de Feign + reintentos + circuit breaker) de las llamadas síncronas.
 * Los errores de negocio (4xx) no cuentan como fallos del circuito y se propagan con su mensaje original.
 */
@Component
@RequiredArgsConstructor
public class ServiciosRemotos {

    private final FlotaClient flota;
    private final ClientesClient clientes;

    @CircuitBreaker(name = "ms-flota", fallbackMethod = "flotaNoDisponibleBus")
    @Retry(name = "ms-flota")
    public FlotaClient.BusInfo validarBus(UUID busId, UUID sucursalId, Integer capacidadMinima) {
        return flota.validarBus(busId, sucursalId, capacidadMinima);
    }

    @CircuitBreaker(name = "ms-flota", fallbackMethod = "flotaNoDisponibleChofer")
    @Retry(name = "ms-flota")
    public FlotaClient.ChoferInfo validarChofer(UUID choferId, UUID sucursalId, LocalDate fecha) {
        return flota.validarChofer(choferId, sucursalId, fecha);
    }

    /** El débito es idempotente por referencia: reintentarlo nunca cobra dos veces. */
    @CircuitBreaker(name = "ms-clientes", fallbackMethod = "clientesNoDisponibleDebito")
    @Retry(name = "ms-clientes")
    public ClientesClient.DebitoResponse debitar(ClientesClient.DebitoRequest req) {
        return clientes.debitar(req.referencia(), req);
    }

    @CircuitBreaker(name = "ms-clientes", fallbackMethod = "clientesNoDisponibleConsulta")
    public ClientesClient.DebitoResponse consultarDebito(String referencia) {
        return clientes.consultarDebito(referencia);
    }

    @CircuitBreaker(name = "ms-clientes", fallbackMethod = "clientesNoDisponibleResolver")
    @Retry(name = "ms-clientes")
    public ClientesClient.ClienteInfo resolverCliente(String nit, String nombre, String email) {
        return clientes.resolver(new ClientesClient.ResolverRequest(nit, nombre, email));
    }

    @SuppressWarnings("unused")
    private FlotaClient.BusInfo flotaNoDisponibleBus(UUID a, UUID b, Integer c, Throwable t) {
        throw falla(t, "flota");
    }

    @SuppressWarnings("unused")
    private FlotaClient.ChoferInfo flotaNoDisponibleChofer(UUID a, UUID b, LocalDate c, Throwable t) {
        throw falla(t, "flota");
    }

    @SuppressWarnings("unused")
    private ClientesClient.DebitoResponse clientesNoDisponibleDebito(ClientesClient.DebitoRequest r, Throwable t) {
        throw falla(t, "clientes y cartera");
    }

    @SuppressWarnings("unused")
    private ClientesClient.DebitoResponse clientesNoDisponibleConsulta(String r, Throwable t) {
        throw falla(t, "clientes y cartera");
    }

    @SuppressWarnings("unused")
    private ClientesClient.ClienteInfo clientesNoDisponibleResolver(String a, String b, String c, Throwable t) {
        throw falla(t, "clientes y cartera");
    }

    private static RuntimeException falla(Throwable t, String servicio) {
        if (t instanceof ApiException api) {
            return api;
        }
        return new ServicioNoDisponibleException("El servicio de " + servicio
                + " no está disponible en este momento. Intenta de nuevo en unos minutos.");
    }
}

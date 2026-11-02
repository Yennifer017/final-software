package gt.rutagt.clientes.application;

import gt.rutagt.clientes.api.dto.ClienteDtos.CarteraResponse;
import gt.rutagt.clientes.api.dto.ClienteDtos.DebitoResponse;
import gt.rutagt.clientes.api.dto.ClienteDtos.MovimientoResponse;
import gt.rutagt.clientes.domain.Cartera;
import gt.rutagt.clientes.domain.Cliente;
import gt.rutagt.clientes.domain.MovimientoCartera;
import gt.rutagt.clientes.infrastructure.persistence.CarteraRepository;
import gt.rutagt.clientes.infrastructure.persistence.ClienteRepository;
import gt.rutagt.clientes.infrastructure.persistence.MovimientoCarteraRepository;
import gt.rutagt.common.errors.ConflictoException;
import gt.rutagt.common.errors.NegocioException;
import gt.rutagt.common.errors.NoEncontradoException;
import gt.rutagt.common.events.RoutingKeys;
import gt.rutagt.common.outbox.OutboxService;
import gt.rutagt.common.web.FechaUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Cartera digital. Garantías:
 * <ul>
 *   <li>Débito atómico ({@code UPDATE ... WHERE saldo >= monto}): nunca saldo negativo, aun con concurrencia.</li>
 *   <li>Idempotencia por referencia única: repetir un débito o reembolso no cobra/acredita dos veces.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CarteraService {

    private final CarteraRepository carteras;
    private final MovimientoCarteraRepository movimientos;
    private final ClienteRepository clientes;
    private final OutboxService outbox;

    @Transactional(readOnly = true)
    public CarteraResponse estado(UUID usuarioId) {
        Cartera c = carteraDe(usuarioId);
        Cliente cliente = clientes.findById(c.getClienteId()).orElseThrow();
        return new CarteraResponse(cliente.getId(), cliente.getNit(), cliente.getNombre(), c.getSaldo(),
                movimientos.findTop100ByCarteraIdOrderByCreadoEnDesc(c.getId()).stream().map(CarteraService::mov).toList());
    }

    @Transactional
    public CarteraResponse recargar(UUID usuarioId, BigDecimal monto, LocalDate fecha) {
        Cartera c = carteraDe(usuarioId);
        carteras.acreditar(c.getId(), monto);
        BigDecimal saldo = carteras.findById(c.getId()).orElseThrow().getSaldo();
        LocalDate dia = FechaUtil.hoySiNulo(fecha);
        movimientos.save(new MovimientoCartera(c.getId(), MovimientoCartera.Tipo.RECARGA, monto, saldo,
                "Recarga manual", null, dia));
        Cliente cliente = clientes.findById(c.getClienteId()).orElseThrow();
        outbox.registrar("Cartera", c.getId(), RoutingKeys.CARTERA_RECARGADA_EVT, RoutingKeys.CARTERA_RECARGADA,
                new Eventos.MovimientoCartera(cliente.getId(), cliente.getEmail(), cliente.getNombre(), monto, saldo,
                        "Recarga manual", dia));
        return estado(usuarioId);
    }

    /** Débito idempotente: si la referencia ya existe se devuelve el mismo resultado sin volver a cobrar. */
    @Transactional
    public DebitoResponse debitar(UUID usuarioId, UUID clienteId, BigDecimal monto, String referencia,
                                  String concepto, LocalDate fecha) {
        Optional<MovimientoCartera> previo = movimientos.findByReferencia(referencia);
        if (previo.isPresent()) {
            return respuestaDebito(previo.get());
        }
        if (monto == null || monto.signum() <= 0) {
            throw new NegocioException("MONTO_INVALIDO", "El monto a debitar debe ser mayor a cero.");
        }
        Cartera c = usuarioId != null ? carteraDe(usuarioId)
                : carteras.findByClienteId(clienteId).orElseThrow(() -> new NoEncontradoException("Cartera no encontrada."));
        if (carteras.debitar(c.getId(), monto) == 0) {
            BigDecimal saldo = carteras.findById(c.getId()).map(Cartera::getSaldo).orElse(BigDecimal.ZERO);
            throw new NegocioException("SALDO_INSUFICIENTE", "Saldo insuficiente en tu cartera: tienes Q "
                    + saldo.setScale(2) + " y necesitas Q " + monto.setScale(2) + ". Recarga tu cartera e intenta de nuevo.");
        }
        BigDecimal saldo = carteras.findById(c.getId()).orElseThrow().getSaldo();
        MovimientoCartera m = movimientos.saveAndFlush(new MovimientoCartera(c.getId(), MovimientoCartera.Tipo.DEBITO,
                monto, saldo, concepto, referencia, FechaUtil.hoySiNulo(fecha)));
        return respuestaDebito(m);
    }

    @Transactional(readOnly = true)
    public DebitoResponse consultarDebito(String referencia) {
        return movimientos.findByReferencia(referencia)
                .filter(m -> m.getTipo() == MovimientoCartera.Tipo.DEBITO)
                .map(this::respuestaDebito)
                .orElseThrow(() -> new NoEncontradoException("No existe un débito con la referencia " + referencia + "."));
    }

    /**
     * Reembolso idempotente de un débito previo (referencia {@code REEMB-<referenciaOriginal>}).
     * Si el débito original no existe (la compra nunca se cobró) no hace nada.
     */
    @Transactional
    public boolean reembolsar(String referenciaOriginal, String concepto) {
        MovimientoCartera debito = movimientos.findByReferencia(referenciaOriginal)
                .filter(m -> m.getTipo() == MovimientoCartera.Tipo.DEBITO).orElse(null);
        String referencia = "REEMB-" + referenciaOriginal;
        if (debito == null || movimientos.existsByReferencia(referencia)) {
            return false;
        }
        carteras.acreditar(debito.getCarteraId(), debito.getMonto());
        Cartera c = carteras.findById(debito.getCarteraId()).orElseThrow();
        movimientos.save(new MovimientoCartera(c.getId(), MovimientoCartera.Tipo.REEMBOLSO, debito.getMonto(),
                c.getSaldo(), concepto, referencia, FechaUtil.hoy()));
        Cliente cliente = clientes.findById(c.getClienteId()).orElseThrow();
        outbox.registrar("Cartera", c.getId(), RoutingKeys.CARTERA_REEMBOLSADA_EVT, RoutingKeys.CARTERA_REEMBOLSADA,
                new Eventos.MovimientoCartera(cliente.getId(), cliente.getEmail(), cliente.getNombre(),
                        debito.getMonto(), c.getSaldo(), concepto, FechaUtil.hoy()));
        return true;
    }

    Cartera carteraDe(UUID usuarioId) {
        return carteras.findByUsuarioId(usuarioId).orElseThrow(() -> new ConflictoException("CUENTA_EN_CONFIGURACION",
                "Tu cuenta aún se está configurando. Intenta de nuevo en unos segundos."));
    }

    private DebitoResponse respuestaDebito(MovimientoCartera m) {
        Cartera c = carteras.findById(m.getCarteraId()).orElseThrow();
        Cliente cliente = clientes.findById(c.getClienteId()).orElseThrow();
        return new DebitoResponse(m.getId(), cliente.getId(), cliente.getNombre(), cliente.getEmail(),
                cliente.getNit(), m.getSaldoResultante(), m.getReferencia());
    }

    static MovimientoResponse mov(MovimientoCartera m) {
        return new MovimientoResponse(m.getId(), m.getTipo().name(), m.getMonto(), m.getSaldoResultante(),
                m.getConcepto(), m.getFechaOperacion(), m.getCreadoEn());
    }
}

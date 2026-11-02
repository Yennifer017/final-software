package gt.rutagt.clientes.application;

import gt.rutagt.clientes.api.dto.ClienteDtos.ClienteInfo;
import gt.rutagt.clientes.api.dto.ClienteDtos.ClienteResponse;
import gt.rutagt.clientes.domain.Cartera;
import gt.rutagt.clientes.domain.Cliente;
import gt.rutagt.clientes.infrastructure.persistence.CarteraRepository;
import gt.rutagt.clientes.infrastructure.persistence.ClienteRepository;
import gt.rutagt.common.errors.ConflictoException;
import gt.rutagt.common.errors.NoEncontradoException;
import gt.rutagt.common.events.RoutingKeys;
import gt.rutagt.common.outbox.OutboxService;
import gt.rutagt.common.web.Nit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Clientes identificados por NIT. Clave del enunciado: las ventas presenciales hechas con un NIT
 * aparecen en el historial cuando esa persona crea su cuenta (se enlaza el cliente existente).
 */
@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clientes;
    private final CarteraRepository carteras;
    private final OutboxService outbox;

    /** Consumidor de UsuarioClienteRegistrado: crea o ENLAZA el cliente por NIT y abre su cartera. */
    @Transactional
    public void registrarUsuario(Eventos.UsuarioClienteRegistrado e) {
        String nit = Nit.normalizar(e.nit());
        Cliente c = clientes.findByNit(nit).orElseGet(() -> {
            Cliente nuevo = new Cliente();
            nuevo.setNit(nit);
            return nuevo;
        });
        if (c.getUsuarioId() != null && !c.getUsuarioId().equals(e.usuarioId())) {
            throw new ConflictoException("NIT_ENLAZADO", "El NIT " + nit + " ya pertenece a otra cuenta.");
        }
        c.setUsuarioId(e.usuarioId());
        c.setNombre(e.nombre());
        c.setEmail(e.email());
        c.setTelefono(e.telefono());
        c.setDireccion(e.direccion());
        c.setDpi(e.dpi());
        c = clientes.save(c);
        if (carteras.findByUsuarioId(e.usuarioId()).isEmpty()) {
            Cartera cartera = new Cartera();
            cartera.setClienteId(c.getId());
            cartera.setUsuarioId(e.usuarioId());
            carteras.save(cartera);
        }
        publicarVinculo(c);
    }

    /**
     * Consumidor de PerfilActualizado. Si el cliente cambia su NIT a uno que ya tenía compras de mostrador,
     * ese registro también se enlaza a su usuario para que el historial aparezca completo.
     */
    @Transactional
    public void actualizarPerfil(Eventos.PerfilActualizado e) {
        if (!"CLIENTE".equals(e.rol())) {
            return;
        }
        Cartera cartera = carteras.findByUsuarioId(e.usuarioId()).orElse(null);
        if (cartera == null) {
            return;
        }
        Cliente principal = clientes.findById(cartera.getClienteId()).orElseThrow();
        String nit = Nit.normalizar(e.nit());
        if (!nit.equals(principal.getNit())) {
            clientes.findByNit(nit).ifPresentOrElse(otro -> {
                if (otro.getUsuarioId() == null && !otro.isEsGenerico()) {
                    otro.setUsuarioId(e.usuarioId());
                    publicarVinculo(otro);
                }
            }, () -> principal.setNit(nit));
        }
        principal.setNombre(e.nombre());
        principal.setEmail(e.email());
        principal.setTelefono(e.telefono());
        principal.setDireccion(e.direccion());
        principal.setDpi(e.dpi());
    }

    /** Interno (ms-viajes, venta de mostrador): obtiene el cliente por NIT o lo crea; sin NIT → genérico CF. */
    @Transactional
    public ClienteInfo resolver(String nit, String nombre, String email) {
        String normal = Nit.normalizar(nit);
        if (Nit.esConsumidorFinal(normal)) {
            Cliente g = clientes.findById(Cliente.GENERICO).orElseThrow();
            return info(g);
        }
        Cliente c = clientes.findByNit(normal).orElseGet(() -> {
            Cliente nuevo = new Cliente();
            nuevo.setNit(normal);
            nuevo.setNombre(nombre == null || nombre.isBlank() ? "Cliente NIT " + normal : nombre.trim());
            nuevo.setEmail(email == null || email.isBlank() ? null : email.trim().toLowerCase());
            return clientes.save(nuevo);
        });
        if (c.getEmail() == null && email != null && !email.isBlank()) {
            c.setEmail(email.trim().toLowerCase());
        }
        return info(c);
    }

    @Transactional(readOnly = true)
    public ClienteResponse porNit(String nit) {
        Cliente c = clientes.findByNit(Nit.normalizar(nit))
                .orElseThrow(() -> new NoEncontradoException("No hay un cliente con ese NIT; se registrará al vender."));
        return respuesta(c);
    }

    @Transactional(readOnly = true)
    public ClienteResponse delUsuario(UUID usuarioId) {
        Cartera cartera = carteras.findByUsuarioId(usuarioId).orElseThrow(() -> new ConflictoException(
                "CUENTA_EN_CONFIGURACION", "Tu cuenta se está configurando. Intenta de nuevo en unos segundos."));
        return respuesta(clientes.findById(cartera.getClienteId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<UUID> idsDelUsuario(UUID usuarioId) {
        return clientes.findByUsuarioId(usuarioId).stream().map(Cliente::getId).toList();
    }

    Cliente buscar(UUID id) {
        return clientes.findById(id).orElseThrow(() -> new NoEncontradoException("Cliente no encontrado."));
    }

    private void publicarVinculo(Cliente c) {
        outbox.registrar("Cliente", c.getId(), RoutingKeys.CLIENTE_VINCULADO_EVT, RoutingKeys.CLIENTE_VINCULADO,
                new Eventos.ClienteVinculado(c.getId(), c.getUsuarioId(), c.getNit(), c.getNombre(), c.getEmail()));
    }

    private ClienteResponse respuesta(Cliente c) {
        BigDecimal saldo = carteras.findByClienteId(c.getId()).map(Cartera::getSaldo).orElse(null);
        return new ClienteResponse(c.getId(), c.getNit(), c.getNombre(), c.getEmail(), c.getTelefono(),
                c.getDireccion(), c.getUsuarioId() != null, c.isEsGenerico(), saldo);
    }

    static ClienteInfo info(Cliente c) {
        return new ClienteInfo(c.getId(), c.getNit(), c.getNombre(), c.getEmail(), c.isEsGenerico());
    }
}

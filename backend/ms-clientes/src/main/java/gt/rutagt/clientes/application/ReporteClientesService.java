package gt.rutagt.clientes.application;

import gt.rutagt.clientes.domain.Alquiler;
import gt.rutagt.clientes.domain.Cliente;
import gt.rutagt.clientes.infrastructure.persistence.AlquilerRepository;
import gt.rutagt.clientes.infrastructure.persistence.ClienteRepository;
import gt.rutagt.common.reportes.ReporteResponse;
import gt.rutagt.common.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static gt.rutagt.common.reportes.ReporteResponse.fila;

@Service
@RequiredArgsConstructor
public class ReporteClientesService {

    private final AlquilerRepository alquileres;
    private final ClienteRepository clientes;
    private final UsuarioActual actual;

    /** Ingresos por alquiler de buses: cliente, origen, destino, fechas, bus asignado y precio total. */
    @Transactional(readOnly = true)
    public ReporteResponse ingresosAlquiler(UUID sucursalId, LocalDate desde, LocalDate hasta) {
        List<Alquiler> lista = alquileres.pagados(actual.sucursalParaConsulta(sucursalId), desde, hasta);
        List<Map<String, Object>> filas = lista.stream().map(a -> fila(
                "codigo", a.getCodigo(),
                "cliente", clientes.findById(a.getClienteId()).map(Cliente::getNombre).orElse("—"),
                "origen", a.getOrigen(), "destino", a.getDestino(), "salida", a.getFechaSalida(),
                "retorno", a.getFechaRetorno(), "bus", a.getBusPlaca() == null ? "Sin asignar" : a.getBusPlaca(),
                "fechaPago", a.getFechaPago(), "precio", a.getPrecioFinal())).toList();
        BigDecimal total = lista.stream().map(Alquiler::getPrecioFinal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return ReporteResponse.builder("Ingresos por alquiler de buses")
                .filtro("Desde", desde).filtro("Hasta", hasta)
                .columna("codigo", "Código", "texto").columna("cliente", "Cliente", "texto")
                .columna("origen", "Origen", "texto").columna("destino", "Destino", "texto")
                .columna("salida", "Salida", "fechaHora").columna("retorno", "Retorno", "fechaHora")
                .columna("bus", "Bus", "placa").columna("fechaPago", "Fecha de pago", "fecha")
                .columna("precio", "Precio total", "moneda")
                .grupo("Alquileres", filas, Map.of("precio", total))
                .total("alquileres", lista.size()).total("precio", total)
                .build();
    }
}

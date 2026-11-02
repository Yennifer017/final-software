package gt.rutagt.flota.application;

import gt.rutagt.common.reportes.ReporteResponse;
import gt.rutagt.flota.api.dto.FlotaDtos.BusResponse;
import gt.rutagt.flota.api.dto.FlotaDtos.ChoferResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static gt.rutagt.common.reportes.ReporteResponse.fila;

/** Listados generales de buses y choferes en el formato común de reportes (exportables a PDF). */
@Service
@RequiredArgsConstructor
public class ReporteFlotaService {

    private final BusService buses;
    private final ChoferService choferes;

    @Transactional(readOnly = true)
    public ReporteResponse buses(UUID sucursalId, String estado) {
        List<BusResponse> lista = buses.listar(sucursalId, estado, null, null, 0, 200).content();
        List<Map<String, Object>> filas = lista.stream().map(b -> fila(
                "placa", b.placa(), "marca", b.marca(), "modelo", b.modelo(), "capacidad", b.capacidad(),
                "estado", b.estadoOperativo(), "choferActual", b.choferActualNombre() == null ? "—" : b.choferActualNombre(),
                "kilometraje", b.kilometrajeActual(), "totalViajes", b.totalViajes())).toList();
        String sucursal = lista.isEmpty() ? null : lista.get(0).sucursalNombre();
        return ReporteResponse.builder("Listado general de buses")
                .filtro("Sucursal", sucursal)
                .filtro("Estado operativo", estado == null || estado.isBlank() ? null : estado)
                .columna("placa", "Placa", "placa").columna("marca", "Marca", "texto")
                .columna("modelo", "Modelo", "texto").columna("capacidad", "Capacidad", "numero")
                .columna("estado", "Estado", "texto").columna("choferActual", "Chofer actual", "texto")
                .columna("kilometraje", "Kilometraje", "numero").columna("totalViajes", "Viajes realizados", "numero")
                .grupo(sucursal == null ? "Buses" : sucursal, filas, Map.of("totalViajes",
                        lista.stream().mapToInt(BusResponse::totalViajes).sum()))
                .total("buses", lista.size())
                .total("totalViajes", lista.stream().mapToInt(BusResponse::totalViajes).sum())
                .build();
    }

    @Transactional(readOnly = true)
    public ReporteResponse choferes(UUID sucursalId, Boolean activo) {
        List<ChoferResponse> lista = choferes.listar(sucursalId, activo, null, null, null, 0, 200).content();
        List<Map<String, Object>> filas = lista.stream().map(c -> fila(
                "licencia", c.numeroLicencia(), "nombre", c.nombreCompleto(), "tipo", c.tipoLicencia(),
                "vencimiento", c.fechaVencimientoLicencia(), "estado", c.activo() ? "ACTIVO" : "INACTIVO",
                "totalViajes", c.totalViajes())).toList();
        String sucursal = lista.isEmpty() ? null : lista.get(0).sucursalNombre();
        return ReporteResponse.builder("Listado general de choferes")
                .filtro("Sucursal", sucursal)
                .filtro("Estado", activo == null ? null : (activo ? "Activos" : "Inactivos"))
                .columna("licencia", "No. licencia", "texto").columna("nombre", "Nombre completo", "texto")
                .columna("tipo", "Tipo", "texto").columna("vencimiento", "Vencimiento", "fecha")
                .columna("estado", "Estado", "texto").columna("totalViajes", "Viajes realizados", "numero")
                .grupo(sucursal == null ? "Choferes" : sucursal, filas, Map.of("totalViajes",
                        lista.stream().mapToInt(ChoferResponse::totalViajes).sum()))
                .total("choferes", lista.size())
                .total("totalViajes", lista.stream().mapToInt(ChoferResponse::totalViajes).sum())
                .build();
    }
}

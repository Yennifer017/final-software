package gt.rutagt.clientes.application;

import gt.rutagt.clientes.domain.Tarifa;
import gt.rutagt.clientes.infrastructure.persistence.TarifaRepository;
import gt.rutagt.common.web.FechaUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/** Reacciones de ms-clientes a eventos de ms-viajes y ms-finanzas. */
@Service
@RequiredArgsConstructor
public class EventosEntrantesService {

    private final CarteraService carteras;
    private final AlquilerService alquileres;
    private final TarifaRepository tarifas;

    /** Viaje cancelado: reembolsa a la cartera cada compra EN LÍNEA que sí se cobró (idempotente). */
    @Transactional
    public void viajeEliminado(Eventos.ViajeEliminado e) {
        if (e.compras() != null) {
            for (Eventos.CompraAnulada c : e.compras()) {
                if ("EN_LINEA".equals(c.canal())) {
                    carteras.reembolsar(c.compraId().toString(),
                            "Reembolso boletos " + c.codigo() + " · viaje cancelado " + e.descripcion());
                }
            }
        }
        alquileres.viajeEliminado(e);
    }

    @Transactional
    public void configuracion(Eventos.ConfiguracionActualizada e) {
        Map<String, BigDecimal> valores = e.valores() == null ? Map.of(e.clave(), e.valor()) : e.valores();
        for (String clave : new String[]{Tarifa.KM, Tarifa.DIA}) {
            BigDecimal valor = valores.get(clave);
            if (valor != null) {
                Tarifa t = tarifas.findById(clave).orElseGet(Tarifa::new);
                t.setClave(clave);
                t.setValor(valor);
                t.setVigenteDesde(e.vigenteDesde() == null ? FechaUtil.hoy() : e.vigenteDesde());
                tarifas.save(t);
            }
        }
    }
}

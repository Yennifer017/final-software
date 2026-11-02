package gt.rutagt.viajes.application;

import gt.rutagt.viajes.api.dto.ViajeDtos.RegistroDto;
import gt.rutagt.viajes.api.dto.ViajeDtos.ViajePublicoResponse;
import gt.rutagt.viajes.api.dto.ViajeDtos.ViajeResponse;
import gt.rutagt.viajes.domain.RegistroLlegada;
import gt.rutagt.viajes.domain.RegistroSalida;
import gt.rutagt.viajes.domain.Viaje;

final class Mapeos {

    private Mapeos() {
    }

    static ViajeResponse viaje(Viaje v, long vendidos, RegistroSalida s, RegistroLlegada l) {
        return new ViajeResponse(v.getId(), v.getTipo().name(), v.getEstado().name(), v.getSucursalId(),
                v.getRuta() == null ? null : v.getRuta().getId(), v.descripcion(), v.getOrigen(), v.getDestino(),
                v.getSalidaProgramada(), v.getLlegadaEstimada(),
                v.getBus() == null ? null : v.getBus().getId(), v.getBus() == null ? null : v.getBus().getPlaca(),
                v.getChofer() == null ? null : v.getChofer().getId(),
                v.getChofer() == null ? null : v.getChofer().getNombre(),
                v.getCapacidad(), v.getPrecioBoleto(), vendidos, v.getPasajerosRequeridos(), v.getAlquilerId(),
                v.getAlquilerCodigo(), v.getClienteNombre(), v.getSalarioChofer(),
                s == null ? null : new RegistroDto(s.getHoraRealSalida(), s.getKmSalida(), null, s.getRegistradoPorNombre()),
                l == null ? null : new RegistroDto(l.getHoraRealLlegada(), l.getKmFinal(), l.getGastoCombustible(),
                        l.getRegistradoPorNombre()));
    }

    static ViajePublicoResponse publico(Viaje v, long ocupados) {
        int capacidad = v.getCapacidad() == null ? 0 : v.getCapacidad();
        return new ViajePublicoResponse(v.getId(), v.getRuta().getId(), v.getRuta().getSucursalOrigenId(),
                v.getRuta().getSucursalOrigenNombre(), v.getRuta().getSucursalDestinoId(),
                v.getRuta().getSucursalDestinoNombre(), v.getSalidaProgramada(), v.getLlegadaEstimada(),
                v.getPrecioBoleto(), v.getBus() == null ? null : v.getBus().getPlaca(),
                v.getBus() == null ? null : v.getBus().getMarca(), v.getBus() == null ? null : v.getBus().getModelo(),
                capacidad, (int) Math.max(0, capacidad - ocupados), v.getRuta().getDistanciaKm());
    }
}

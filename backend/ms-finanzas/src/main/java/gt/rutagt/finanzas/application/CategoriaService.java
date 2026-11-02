package gt.rutagt.finanzas.application;

import gt.rutagt.common.errors.ConflictoException;
import gt.rutagt.common.errors.NoEncontradoException;
import gt.rutagt.finanzas.api.dto.FinanzasDtos.CategoriaResponse;
import gt.rutagt.finanzas.domain.CategoriaGasto;
import gt.rutagt.finanzas.infrastructure.persistence.CategoriaGastoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Catálogo administrable de categorías de gasto (se agregan sin modificar código). */
@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaGastoRepository categorias;

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar(Boolean soloActivas) {
        return (Boolean.TRUE.equals(soloActivas) ? categorias.findByActivaTrueOrderByNombreAsc()
                : categorias.findAllByOrderByNombreAsc()).stream().map(this::respuesta).toList();
    }

    @Transactional
    public CategoriaResponse crear(String nombre) {
        String limpio = nombre.trim();
        if (categorias.existsByNombreIgnoreCase(limpio)) {
            throw new ConflictoException("CATEGORIA_EXISTENTE", "Ya existe la categoría " + limpio + ".");
        }
        CategoriaGasto c = new CategoriaGasto();
        c.setNombre(limpio);
        return respuesta(categorias.save(c));
    }

    @Transactional
    public CategoriaResponse renombrar(UUID id, String nombre) {
        CategoriaGasto c = buscar(id);
        if (categorias.existsByNombreIgnoreCaseAndIdNot(nombre.trim(), id)) {
            throw new ConflictoException("CATEGORIA_EXISTENTE", "Ya existe la categoría " + nombre.trim() + ".");
        }
        c.setNombre(nombre.trim());
        return respuesta(c);
    }

    @Transactional
    public CategoriaResponse cambiarEstado(UUID id, boolean activa) {
        CategoriaGasto c = buscar(id);
        c.setActiva(activa);
        return respuesta(c);
    }

    /** Solo se elimina si no tiene gastos; si los tiene, únicamente puede desactivarse. */
    @Transactional
    public void eliminar(UUID id) {
        CategoriaGasto c = buscar(id);
        long gastos = categorias.gastosAsociados(id);
        if (gastos > 0) {
            throw new ConflictoException("CATEGORIA_CON_GASTOS", "La categoría " + c.getNombre() + " tiene " + gastos
                    + " gasto(s) asociado(s); no se puede eliminar, solo desactivar.");
        }
        categorias.delete(c);
    }

    CategoriaGasto buscar(UUID id) {
        return categorias.findById(id).orElseThrow(() -> new NoEncontradoException("Categoría no encontrada."));
    }

    private CategoriaResponse respuesta(CategoriaGasto c) {
        return new CategoriaResponse(c.getId(), c.getNombre(), c.isActiva(), categorias.gastosAsociados(c.getId()));
    }
}

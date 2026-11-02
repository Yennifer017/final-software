package gt.rutagt.usuarios.application;

import gt.rutagt.usuarios.domain.Rol;
import gt.rutagt.usuarios.domain.Usuario;
import gt.rutagt.usuarios.infrastructure.persistence.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Garantiza que exista un administrador del sistema al arrancar por primera vez (datos de BOOTSTRAP_ADMIN_*). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInicial implements ApplicationRunner {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;

    @Value("${rutagt.bootstrap.admin-email:admin@rutagt.com}")
    private String email;
    @Value("${rutagt.bootstrap.admin-password:Admin12345}")
    private String password;
    @Value("${rutagt.bootstrap.admin-nombre:Administrador del Sistema}")
    private String nombre;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarios.existsByRol(Rol.ADMIN_SISTEMA)) {
            return;
        }
        Usuario u = new Usuario();
        u.setEmail(email.trim().toLowerCase());
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRol(Rol.ADMIN_SISTEMA);
        u.setNombreCompleto(nombre);
        u.setNit("CF");
        u.setDpi("0000000000000");
        u.setTelefono("00000000");
        u.setDireccion("Oficinas centrales");
        usuarios.save(u);
        log.info("Administrador inicial creado: {}", u.getEmail());
    }
}

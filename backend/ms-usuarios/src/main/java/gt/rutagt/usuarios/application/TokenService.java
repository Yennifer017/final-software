package gt.rutagt.usuarios.application;

import gt.rutagt.usuarios.domain.RefreshToken;
import gt.rutagt.usuarios.domain.Usuario;
import gt.rutagt.usuarios.infrastructure.persistence.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;

/** Emite el JWT de acceso (corto) y el refresh token (largo, guardado como hash). */
@Service
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder encoder;
    private final RefreshTokenRepository refreshTokens;
    private final Duration duracionAcceso;
    private final Duration duracionRefresh;

    public TokenService(JwtEncoder encoder, RefreshTokenRepository refreshTokens,
                        @Value("${rutagt.jwt.access-minutes:60}") long minutosAcceso,
                        @Value("${rutagt.jwt.refresh-days:7}") long diasRefresh) {
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.duracionAcceso = Duration.ofMinutes(minutosAcceso);
        this.duracionRefresh = Duration.ofDays(diasRefresh);
    }

    public String generarAcceso(Usuario u) {
        Instant ahora = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("rutagt")
                .subject(u.getId().toString())
                .issuedAt(ahora)
                .expiresAt(ahora.plus(duracionAcceso))
                .claim("rol", u.getRol().name())
                .claim("nombre", u.getNombreCompleto())
                .claim("email", u.getEmail())
                .claim("nit", u.getNit());
        if (u.sucursalId() != null) {
            claims.claim("sucursalId", u.sucursalId().toString());
        }
        if (u.getChoferId() != null) {
            claims.claim("choferId", u.getChoferId().toString());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    public String generarRefresh(Usuario u) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken rt = new RefreshToken();
        rt.setUsuario(u);
        rt.setTokenHash(hash(token));
        rt.setExpiraEn(OffsetDateTime.now().plus(duracionRefresh));
        refreshTokens.save(rt);
        return token;
    }

    public long segundosAcceso() {
        return duracionAcceso.toSeconds();
    }

    public static String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

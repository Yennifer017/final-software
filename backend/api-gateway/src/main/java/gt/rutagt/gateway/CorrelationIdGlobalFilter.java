package gt.rutagt.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Asigna un X-Correlation-Id a cada petición para seguirla en los logs de todos los servicios y eventos. */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String id = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        ServerWebExchange mutado = exchange.mutate()
                .request(r -> r.headers(h -> h.set(HEADER, correlationId)))
                .build();
        mutado.getResponse().getHeaders().set(HEADER, correlationId);
        return chain.filter(mutado);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

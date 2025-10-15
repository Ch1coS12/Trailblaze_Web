package pt.unl.fct.di.apdc.trailblaze.filters;

import java.io.IOException;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;      // <-- este
import jakarta.ws.rs.core.MultivaluedMap;       // <-- este resolve o erro
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;              // <-- este

@Provider
@PreMatching
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String ALLOWED_METHODS =
        "GET,POST,PUT,DELETE,OPTIONS,HEAD";
    private static final String ALLOWED_HEADERS =
        "Origin,Content-Type,Accept,Authorization";

    /* ---------- Request filter ---------- */
    @Override
    public void filter(ContainerRequestContext req) throws IOException {
        // Tratar pré‑flight
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            Response.ResponseBuilder rb = Response.ok();
            addCorsHeaders(rb, req.getHeaderString("Origin"));
            req.abortWith(rb.build());      // já sai com 200 + cabeçalhos
        }
    }

    /* ---------- Response filter ---------- */
    @Override
    public void filter(ContainerRequestContext req,
                       ContainerResponseContext res) throws IOException {

        addCorsHeaders(res.getHeaders(), req.getHeaderString("Origin"));
    }

    /* ---------- Helpers ---------- */
    private static void addCorsHeaders(Response.ResponseBuilder rb, String origin) {
        rb.header("Access-Control-Allow-Origin", origin != null ? origin : "*")
          .header("Access-Control-Allow-Credentials", "true")
          .header("Access-Control-Allow-Headers", ALLOWED_HEADERS)
          .header("Access-Control-Allow-Methods", ALLOWED_METHODS)
          .header("Vary", "Origin");               // evita cache marada
    }

    private static void addCorsHeaders(MultivaluedMap<String,Object> headers,
                                       String origin) {
        headers.putSingle("Access-Control-Allow-Origin", origin != null ? origin : "*");
        headers.putSingle("Access-Control-Allow-Credentials", "true");
        headers.putSingle("Access-Control-Allow-Headers", ALLOWED_HEADERS);
        headers.putSingle("Access-Control-Allow-Methods", ALLOWED_METHODS);
        headers.putSingle("Vary", "Origin");
    }
}

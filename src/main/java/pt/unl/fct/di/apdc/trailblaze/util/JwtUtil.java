package pt.unl.fct.di.apdc.trailblaze.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import java.util.List;
public final class JwtUtil {

    private static final Key KEY =
            Keys.hmacShaKeyFor("segredoSuperSecretoDe256BitsParaJWT!1234567890".getBytes());

    private static final long EXPIRATION_TIME_MS = 1000L * 60 * 60 * 2;

 
    public static String generateToken(String username, List<String> roles) {

        String primaryRole = roles.isEmpty() ? "RU" : roles.get(0);

        String jti       = UUID.randomUUID().toString();
        long   expMillis = System.currentTimeMillis() + EXPIRATION_TIME_MS;

        // Debug logging
        System.out.println("Generating JWT for user: " + username);
        System.out.println("Roles list: " + roles);
        System.out.println("Primary role: " + primaryRole);
        String token = Jwts.builder()
                .setId(jti)
                .setSubject(username)

                // NOVO – mantém ambos:
                .claim("roles", roles)        // lista completa
                .claim("role",  primaryRole)  // legacy

                .setIssuedAt(new Date())
                .setExpiration(new Date(expMillis))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();

        ActiveJwtUtil.register(username, jti, expMillis);
        return token;
    }

    public static Jws<Claims> validateToken(String token) throws JwtException {

        Jws<Claims> jws = Jwts.parserBuilder()
                              .setSigningKey(KEY)
                              .build()
                              .parseClaimsJws(token);

        Claims claims = jws.getBody();

        if (TokenBlacklistUtil.isBlacklisted(claims.getId())) {
            throw new JwtException("Token revogado.");
        }


        return jws;
    }

    // Novo método para obter username (folhas exec)
    public static String getUsername(String token) {
        try {
            if (token != null && token.startsWith("Bearer "))
                token = token.substring("Bearer ".length());
            return validateToken(token).getBody().getSubject();
        } catch (JwtException e) {
            return null;
        }
    }


    // Novo método para obter role (folhas exec)
    public static String getUserRole(String token) {
        try {
            if (token != null && token.startsWith("Bearer "))
                token = token.substring("Bearer ".length());
            return validateToken(token).getBody().get("role", String.class);
        } catch (JwtException e) {
            return null;
        }
    }

    public static List<String> getUserRoles(String token) {
        try {
            if (token != null && token.startsWith("Bearer "))
                token = token.substring("Bearer ".length());

            return validateToken(token).getBody().get("roles", List.class);
        } catch (JwtException e) {
            return Collections.emptyList();
        }
    }

    public static boolean userHasRole(String token, String requiredRole) {
        List<String> roles = getUserRoles(token);
        return roles.contains(requiredRole);
    }


    private JwtUtil() { }

}


package org.jobits.ottos.identity.security;

import org.jobits.ottos.identity.domain.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Issues HS256-signed access tokens carrying the user's roles and effective permissions.
 * The user's roles and permissions must be loaded.
 */
@Service
public class TokenService {

    static final String STAFF = "STAFF";

    private final JwtEncoder encoder;
    private final JwtProperties props;

    TokenService(JwtEncoder encoder, JwtProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public IssuedToken issueAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .subject(user.getId().toString())
                .claim(SecurityConfig.TYPE_CLAIM, STAFF)
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim(SecurityConfig.ROLES_CLAIM, List.copyOf(user.roleCodes()))
                .claim(SecurityConfig.PERMISSIONS_CLAIM, List.copyOf(user.permissionCodes()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, props.accessTokenTtl().toSeconds());
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}

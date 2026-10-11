package org.jobits.ottos.identity.security;

import org.jobits.ottos.identity.domain.ClientType;
import org.jobits.ottos.identity.domain.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Implementation of {@link TokenService}. */
@Service
class TokenServiceImpl implements TokenService {

    static final String STAFF = "STAFF";
    static final String CLIENT_CLAIM = "client";

    private final JwtEncoder encoder;
    private final JwtProperties props;

    TokenServiceImpl(JwtEncoder encoder, JwtProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public IssuedToken issueAccessToken(User user, ClientType client) {
        Instant now = Instant.now();
        Duration ttl = client == ClientType.MOBILE ? props.mobileAccessTokenTtl() : props.accessTokenTtl();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(user.getId().toString())
                .claim(SecurityConfig.TYPE_CLAIM, STAFF)
                .claim(CLIENT_CLAIM, client.name())
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim(SecurityConfig.ROLES_CLAIM, List.copyOf(user.roleCodes()))
                .claim(SecurityConfig.PERMISSIONS_CLAIM, List.copyOf(user.permissionCodes()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, ttl.toSeconds());
    }
}

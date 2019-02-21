/*
 * Copyright (C) 2010-2015 AludraTest.org and the contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aludratest.cloud.web.security.jwt;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.aludratest.cloud.user.User;
import org.aludratest.cloud.web.security.CloudManagerAuthenticationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Clock;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;

@Component
public class JwtTokenValidator {

	private JwtTokenKey tokenKey;

	private Clock jwsClock;

	@Autowired
	public JwtTokenValidator(JwtTokenKey tokenKey, JwtTimeService timeService) {
		this.tokenKey = tokenKey;
		jwsClock = createJwsClock(timeService);
	}

	public JwtAuthenticationToken validateToken(String tokenString) throws AuthenticationException {
		try {
			JwtParser parser = Jwts.parser().setClock(jwsClock).setSigningKey(tokenKey.getSigningKey());
			Jws<Claims> claimsJws = parser.parseClaimsJws(tokenString);
			checkAlgorithm(claimsJws.getHeader().getAlgorithm());

			Claims body = claimsJws.getBody();
			checkExpiration(body);

			List<GrantedAuthority> authorities = getAuthorities(body);

			// this is the JWT idea: do NOT get user object from User database, but
			// reconstruct from JWT body
			User user = createUserFromBody(body,
					authorities.contains(CloudManagerAuthenticationProvider.ADMIN_AUTHORITY));

			return new JwtAuthenticationToken(user, tokenString, authorities);
		} catch (JwtException e) {
			throw new BadCredentialsException("The JWT is invalid", e);
		}
	}

	private User createUserFromBody(Claims body, final boolean admin) {

		return new User() {

			@Override
			public String getName() {
				return body.get(JwtTokenGenerator.CLAIM_KEY_USERNAME, String.class);
			}

			@Override
			public String[] getDefinedUserAttributes() {
				// currently not supported by JWT
				return new String[0];
			}

			@Override
			public String getUserAttribute(String attributeKey) {
				// currently not supported by JWT
				return null;
			}

			@Override
			public String getSource() {
				return "jwt";
			}

			@Override
			public boolean isAdmin() {
				return admin;
			}

		};
	}

	private List<GrantedAuthority> getAuthorities(Claims claims) {
		Object authClaim = claims.get(JwtTokenGenerator.CLAIM_KEY_AUTHORIZATION);
		if (authClaim == null) {
			throw new JwtException("JWT does not contain authorization claim");
		}

		if (!(authClaim instanceof List<?>)) {
			throw new JwtException("JWT authorization claim is not a list");
		}
		List<?> roleTokens = (List<?>) authClaim;
		return roleTokens.stream().map(element -> toGrantedAuthority(element)).filter(element -> element != null)
				.collect(Collectors.toList());
	}

	private GrantedAuthority toGrantedAuthority(Object o) {
		// TODO this is not very extensible. Check some dynamic mode.
		if ("ROLE_ADMIN".equals(o)) {
			return CloudManagerAuthenticationProvider.ADMIN_AUTHORITY;
		}
		if ("ROLE_USER".equals(o)) {
			return CloudManagerAuthenticationProvider.USER_AUTHORITY;
		}

		return null;
	}

	private void checkAlgorithm(String usedAlgorithm) {
		if (!usedAlgorithm.equals(JwtTokenGenerator.SIGNATURE_ALGORITHM.getValue())) {
			throw new JwtException("Invalid JWT signing algorithm used: " + usedAlgorithm);
		}
	}

	private void checkExpiration(Claims claims) {
		Date expiration = claims.get("exp", Date.class);
		if (expiration == null) {
			throw new JwtException("JWT token does not contain expiration data");
		}

		Date now = jwsClock.now();
		if (now.after(expiration)) {
			throw new JwtException("JWT token expired");
		}
	}

	private static Clock createJwsClock(final JwtTimeService timeService) {
		return new Clock() {
			@Override
			public Date now() {
				return Date.from(timeService.now().atZone(ZoneId.systemDefault()).toInstant());
			}
		};
	}
}
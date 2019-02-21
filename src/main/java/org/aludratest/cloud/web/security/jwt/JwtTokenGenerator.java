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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAmount;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.aludratest.cloud.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@Component
public class JwtTokenGenerator {

	public static final String CLAIM_KEY_AUTHORIZATION = "authorization";
	public static final String CLAIM_KEY_USERNAME = "username";

	public static final SignatureAlgorithm SIGNATURE_ALGORITHM = SignatureAlgorithm.HS512;

	private static final TemporalAmount JWT_EXPIRATION_PERIOD = Duration.ofHours(8);

	private JwtTokenKey tokenKey;

	@Autowired
	public JwtTokenGenerator(JwtTokenKey tokenKey) {
		this.tokenKey = tokenKey;
	}

	public String generateToken(Authentication authentication) {
		if (!(authentication.getPrincipal() instanceof User)) {
			throw new IllegalArgumentException("Cannot generate JWT for non-ACM authentication");
		}

		User user = (User) authentication.getPrincipal();

		LocalDateTime expirationDateTime = LocalDateTime.now().plus(JWT_EXPIRATION_PERIOD);
		Date expirationDate = Date.from(expirationDateTime.atZone(ZoneId.systemDefault()).toInstant());

		Claims claims = Jwts.claims();
		claims.setExpiration(expirationDate);

		claims.put(CLAIM_KEY_USERNAME, user.getName());
		claims.put(CLAIM_KEY_AUTHORIZATION, getAuthorizationClaim(authentication));


		return Jwts.builder().setClaims(claims).signWith(SIGNATURE_ALGORITHM, tokenKey.getSigningKey()).compact();
	}

	private List<String> getAuthorizationClaim(Authentication authentication) {
		return authentication.getAuthorities().stream()
				.map(auth -> auth.getAuthority())
				.collect(Collectors.toList());
	}
}
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

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

public class JwtAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

	private static final String HTTP_AUTHORIZATION_HEADER = "Authorization";

	private JwtTokenValidator tokenValidator;

	public JwtAuthenticationFilter(@Autowired JwtTokenValidator tokenValidator, RequestMatcher matcher) {
		super(matcher);
		this.tokenValidator = tokenValidator;
	}

	@Override
	protected boolean requiresAuthentication(HttpServletRequest request, HttpServletResponse response) {
		if (!super.requiresAuthentication(request, response)) {
			return false;
		}

		// also, return false if no Authorization: Bearer header present, as then, the
		// httpBasic() filter will do its work
		String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
		return authHeader != null && authHeader.startsWith("Bearer ");
	}

	@Override
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
			throws AuthenticationException {
		String tokenString = extractAuthToken(request);
		JwtAuthenticationToken token = tokenValidator.validateToken(tokenString);

		logger.debug("User " + token.getUser().getName() + " authenticated for "
				+ request.getRequestURI() + " (" + request.getMethod() + ")");

		return getAuthenticationManager().authenticate(token);
	}

	@Override
	protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException failed) throws IOException, ServletException {
		super.unsuccessfulAuthentication(request, response, failed);
	}

	@Override
	protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
			Authentication authResult) throws IOException, ServletException {
		super.successfulAuthentication(request, response, chain, authResult);

		chain.doFilter(request, response);
	}

	private String extractAuthToken(HttpServletRequest request) throws AuthenticationException {
		String authorizationHeader = request.getHeader(HTTP_AUTHORIZATION_HEADER);
		final String headerPrefix = "Bearer ";
		if (authorizationHeader == null || !authorizationHeader.startsWith(headerPrefix)) {
			throw new InsufficientAuthenticationException("No JWT token found in request headers");
		}
		return authorizationHeader.substring(headerPrefix.length());
	}
}
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
package org.aludratest.cloud.web.security;

import java.util.ArrayList;
import java.util.List;

import org.aludratest.cloud.user.StoreException;
import org.aludratest.cloud.user.User;
import org.aludratest.cloud.user.UserDatabase;
import org.aludratest.cloud.user.admin.UserDatabaseRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CloudManagerAuthenticationProvider implements AuthenticationProvider {

	private static final Log LOG = LogFactory.getLog(CloudManagerAuthenticationProvider.class);

	public static final GrantedAuthority USER_AUTHORITY = new SimpleGrantedAuthority("ROLE_USER");

	public static final GrantedAuthority ADMIN_AUTHORITY = new SimpleGrantedAuthority("ROLE_ADMIN");

	private UserDatabaseRegistry userDatabaseRegistry;

	@Autowired
	public CloudManagerAuthenticationProvider(UserDatabaseRegistry userDatabaseRegistry) {
		this.userDatabaseRegistry = userDatabaseRegistry;
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		if (!(authentication instanceof UsernamePasswordAuthenticationToken)) {
			throw new BadCredentialsException("Unsupported authentication type: " + authentication.getClass().getName());
		}

		// maybe JWT authentication by filter?
		if (authentication.isAuthenticated()) {
			return authentication;
		}

		String username = (String) authentication.getPrincipal();
		String password = (String) authentication.getCredentials();

		if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password)) {
			throw new BadCredentialsException("Username or password is empty");
		}

		UserDatabase users = userDatabaseRegistry.getSelectedUserDatabase();
		if (users == null) {
			LOG.error("No user database configured. Cannot authenticate user " + username);
			throw new InternalAuthenticationServiceException("No user database configured");
		}

		// we could, but we won't distinguish between user not found and incorrect password, so just authenticate...
		try {
			User user = users.authenticate(username, password);
			if (user == null) {
				throw new BadCredentialsException("Username / password combination is invalid");
			}

			// build granted authorities
			List<GrantedAuthority> authorities = new ArrayList<>();
			authorities.add(USER_AUTHORITY);
			if (user.isAdmin()) {
				authorities.add(ADMIN_AUTHORITY);
			}

			return new UsernamePasswordAuthenticationToken(user, null, authorities);
		}
		catch (StoreException e) {
			throw new AuthenticationServiceException("Could not query user database", e);
		}
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}

}

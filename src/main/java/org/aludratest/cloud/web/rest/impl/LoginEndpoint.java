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
package org.aludratest.cloud.web.rest.impl;

import org.aludratest.cloud.user.User;
import org.aludratest.cloud.web.security.jwt.JwtTokenGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginEndpoint {

	private JwtTokenGenerator tokenGenerator;

	private static final Log log = LogFactory.getLog(LoginEndpoint.class);

	@Autowired
	public LoginEndpoint(JwtTokenGenerator tokenGenerator) {
		this.tokenGenerator = tokenGenerator;
	}

	@PostMapping(value = "/api/login")
	public ResponseEntity<String> login(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		User user = (User) authentication.getPrincipal();
		log.debug("User " + user.getName() + " logged in via REST endpoint");

		return ResponseEntity.ok(tokenGenerator.generateToken(authentication));
	}

	@PostMapping(value = "/api/refreshLogin")
	public ResponseEntity<String> refreshLogin(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof User)
				|| !authentication.isAuthenticated()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		return login(authentication);
	}

}

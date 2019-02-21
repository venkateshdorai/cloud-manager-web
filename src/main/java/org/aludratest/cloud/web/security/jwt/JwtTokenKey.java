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

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenKey {

	@Autowired
	private Environment environment;

	private byte[] signingKey;

	public JwtTokenKey() {
		// generate a random signing key (will change at next startup)
		// try to use SecureRandom, if available
		Random random;
		try {
			random = SecureRandom.getInstanceStrong();
		} catch (NoSuchAlgorithmException e) {
			LogFactory.getLog(JwtTokenGenerator.class)
					.warn("No SecureRandom available. Using pseudorandom for generating JWT signing key", e);
			// fallback to pseudorandom
			random = new Random();
		}

		signingKey = new byte[32];
		random.nextBytes(signingKey);
	}

	public byte[] getSigningKey() {
		// during (some) unit tests or in DEV environment, use constant key
		// in production, use random key
		if (environment != null && environment.acceptsProfiles("dev")) {
			try {
				return "JwTt0Kn".getBytes("UTF-8");
			} catch (UnsupportedEncodingException e) {
				// No UTF-8 - no sweets
				throw new RuntimeException(e);
			}
		}
		return signingKey;
	}
}

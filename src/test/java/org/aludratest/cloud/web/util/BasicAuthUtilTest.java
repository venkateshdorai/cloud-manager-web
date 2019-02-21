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
package org.aludratest.cloud.web.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.aludratest.cloud.user.User;
import org.aludratest.cloud.user.UserDatabase;
import org.junit.Test;

public class BasicAuthUtilTest {

	@Test
	public void testSuccessfulAuth() throws Exception {
		// build an authentication header - also includes a UTF-8 specific character
		String authHeader = "Basic " + Base64.getEncoder().encodeToString("testuser:pä$$w0rd".getBytes("UTF-8"));

		UserDatabase userDb = mockUserDatabase();
		User user = BasicAuthUtil.authenticate(authHeader, userDb);
		assertEquals("testuser", user.getName());
	}

	@Test
	public void testUnsuccessfulAuth() throws Exception {
		// build an authentication header
		String authHeader = "Basic " + Base64.getEncoder().encodeToString("testuser:any".getBytes("UTF-8"));

		UserDatabase userDb = mockUserDatabase();
		try {
			BasicAuthUtil.authenticate(authHeader, userDb);
			fail("authenticate should have failed with IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// OK
		}
	}

	@Test
	public void testHttpAuthProcess() throws Exception {
		UserDatabase userDb = mockUserDatabase();

		// 1. Request without Authorization header
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);

		final Map<String, String> headers = new HashMap<>();
		final AtomicInteger status = new AtomicInteger(0);

		doAnswer((inv) -> {
			headers.put(inv.getArgument(0), inv.getArgument(1));
			return null;
		}).when(response).setHeader(any(), any());
		doAnswer((inv) -> {
			status.set(inv.<Integer>getArgument(0));
			return null;
		}).when(response).sendError(any(int.class));

		User user = BasicAuthUtil.authenticate(request, response, userDb);

		assertNull(user);
		assertTrue(headers.get("WWW-Authenticate").startsWith("Basic"));
		assertEquals(401, status.get());

		// reset response headers and status
		headers.clear();
		status.set(0);

		// invalid password
		when(request.getHeader("Authorization"))
				.thenReturn("Basic " + Base64.getEncoder().encodeToString("testuser:any".getBytes("UTF-8")));
		user = BasicAuthUtil.authenticate(request, response, userDb);

		assertNull(user);
		assertNull(headers.get("WWW-Authenticate"));
		assertEquals(403, status.get());

		headers.clear();
		status.set(0);

		// valid password
		when(request.getHeader("Authorization"))
				.thenReturn("Basic " + Base64.getEncoder().encodeToString("testuser:pä$$w0rd".getBytes("UTF-8")));
		user = BasicAuthUtil.authenticate(request, response, userDb);

		assertNotNull(user);
		assertNull(headers.get("WWW-Authenticate"));
		assertEquals(0, status.get());
	}

	private UserDatabase mockUserDatabase() throws Exception {
		UserDatabase userDb = mock(UserDatabase.class);
		User user = mock(User.class);

		when(user.getName()).thenReturn("testuser");
		when(user.getSource()).thenReturn("test");

		when(userDb.authenticate("testuser", "pä$$w0rd")).thenReturn(user);
		return userDb;
	}

}

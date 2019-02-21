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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.aludratest.cloud.app.CloudManagerAppConfig;
import org.aludratest.cloud.app.CloudManagerAppConfigAdmin;
import org.aludratest.cloud.app.CloudManagerAppSettings;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public class ConfigEndpointTest {

	private static final String[] configKeys = { "hostName", "useProxy", "proxyHost", "proxyPort",
			"bypassProxyRegex" };

	@Test
	public void testGetConfig() {
		CloudManagerAppSettings settings = mockSettings();

		CloudManagerAppConfig config = mock(CloudManagerAppConfig.class);
		when(config.getCurrentSettings()).thenReturn(settings);

		ConfigEndpoint ce = new ConfigEndpoint(config);
		ResponseEntity<String> resp = ce.getBasicConfig();
		assertEquals(200, resp.getStatusCodeValue());

		JSONObject obj = new JSONObject(resp.getBody());
		JSONArray configObj = obj.getJSONObject("result").getJSONArray("config");

		assertEquals(configKeys.length, configObj.length());

		for (int i = 0; i < configObj.length(); i++) {
			JSONObject elem = configObj.getJSONObject(i);
			assertNotNull(elem.getString("description"));
			assertTrue("Invalid config key found in response: " + elem.getString("key"),
					Arrays.asList(configKeys).contains(elem.getString("key")));
		}

		assertConfigKeyValue(configObj, "hostName", "testhost");
		assertConfigKeyValue(configObj, "useProxy", Boolean.TRUE);
		assertConfigKeyValue(configObj, "proxyHost", "testproxy");
		assertConfigKeyValue(configObj, "proxyPort", 1000);
		assertConfigKeyValue(configObj, "bypassProxyRegex", "none");
	}

	@Test
	public void testSetConfig() {
		final Map<String, Object> values = new HashMap<>();
		final AtomicBoolean commitCalled = new AtomicBoolean();

		Answer<Void> answer = (inv) -> {
			if (inv.getMethod().getName().startsWith("set")) {
				values.put(inv.getMethod().getName(), inv.getArgument(0));
			}
			if ("commit".equals(inv.getMethod().getName())) {
				commitCalled.set(true);
			}
			return null;
		};
		CloudManagerAppConfigAdmin admin = mock(CloudManagerAppConfigAdmin.class, answer);
		CloudManagerAppConfig config = mock(CloudManagerAppConfig.class);
		when(config.getAdminInterface(CloudManagerAppConfigAdmin.class)).thenReturn(admin);
		CloudManagerAppSettings settings = mockSettings();
		when(config.getCurrentSettings()).thenReturn(settings);

		ConfigEndpoint ce = new ConfigEndpoint(config);

		// set single value
		MultiValueMap<String, String> requestValues = new LinkedMultiValueMap<>();
		requestValues.put("proxyHost", Collections.singletonList("newProxy"));
		ResponseEntity<String> result = ce.setBasicConfig(requestValues);
		assertEquals(200, result.getStatusCodeValue());

		assertEquals("newProxy", values.get("setProxyHost"));
		assertEquals(1, values.size());
		assertTrue(commitCalled.get());

		// set multiple values (just add another one)
		values.clear();
		commitCalled.set(false);

		requestValues.put("proxyPort", Collections.singletonList("3000"));
		result = ce.setBasicConfig(requestValues);
		assertEquals(200, result.getStatusCodeValue());

		assertEquals("newProxy", values.get("setProxyHost"));
		assertEquals(3000, values.get("setProxyPort"));
		assertEquals(2, values.size());
		assertTrue(commitCalled.get());

		// add yet another config value, which should just be ignored
		values.clear();

		requestValues.put("notExisting", Collections.singletonList("1"));
		result = ce.setBasicConfig(requestValues);
		assertEquals(200, result.getStatusCodeValue());
		assertEquals(2, values.size());

		// invalid port number
		commitCalled.set(false);

		requestValues.put("proxyPort", Collections.singletonList("99999"));
		result = ce.setBasicConfig(requestValues);
		assertEquals(400, result.getStatusCodeValue());
		assertFalse(commitCalled.get());
	}

	private void assertConfigKeyValue(JSONArray config, String key, Object value) {
		for (int i = 0; i < config.length(); i++) {
			JSONObject elem = config.getJSONObject(i);
			if (elem.getString("key").equals(key)) {
				if (value instanceof String) {
					assertEquals(value, elem.getString("value"));
				} else if (value instanceof Boolean) {
					assertEquals(value, elem.getBoolean("value"));
				} else if (value instanceof Integer) {
					assertEquals(value, elem.getInt("value"));
				}
			}
		}
	}

	private CloudManagerAppSettings mockSettings() {
		CloudManagerAppSettings settings = mock(CloudManagerAppSettings.class);
		when(settings.getHostName()).thenReturn("testhost");
		when(settings.isUseProxy()).thenReturn(Boolean.TRUE);
		when(settings.getProxyHost()).thenReturn("testproxy");
		when(settings.getProxyPort()).thenReturn(1000);
		when(settings.getBypassProxyRegexp()).thenReturn("none");
		return settings;
	}

}

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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.aludratest.cloud.app.CloudManagerAppConfig;
import org.aludratest.cloud.app.CloudManagerAppConfigAdmin;
import org.aludratest.cloud.app.CloudManagerAppSettings;
import org.aludratest.cloud.config.ConfigException;
import org.aludratest.cloud.web.rest.AbstractRestController;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for setting and retrieving basic AludraTest Cloud Manager configuration. Accessible via <code>/api/config</code>.
 *
 * @author falbrech
 *
 */
@RestController
public class ConfigEndpoint extends AbstractRestController {

	private static final String HOST_NAME_KEY = "hostName";

	private static final String HOST_NAME_DESCRIPTION = "The external name of the host AludraTest Cloud Manager is running on. Used when constructing absolute URLs in headers or responses.";

	private static final String USE_PROXY_KEY = "useProxy";

	private static final String USE_PROXY_DESCRIPTION = "If activated, uses a proxy for connections to external systems and resources. Proxy host and port must in this case also be specified.";

	private static final String PROXY_HOST_KEY = "proxyHost";

	private static final String PROXY_HOST_DESCRIPTION = "The name or IP of the proxy host to use, when a proxy shall be used.";

	private static final String PROXY_PORT_KEY = "proxyPort";

	private static final String PROXY_PORT_DESCRIPTION = "The TCP port of the proxy host to use, when a proxy shall be used.";

	private static final String BYPASS_PROXY_REGEX_KEY = "bypassProxyRegex";

	private static final String BYPASS_PROXY_REGEX_DESCRIPTION = "Regular expression for hosts to connect to directly instead of using a proxy, when using a proxy.";

	private static final Set<String> VALID_CONFIG_KEYS = new HashSet<>(
			Arrays.asList(new String[] { HOST_NAME_KEY, USE_PROXY_KEY, PROXY_HOST_KEY, PROXY_PORT_KEY, BYPASS_PROXY_REGEX_KEY }));

	private CloudManagerAppConfig applicationConfig;

	@Autowired
	public ConfigEndpoint(CloudManagerAppConfig applicationConfig) {
		this.applicationConfig = applicationConfig;
	}

	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/config", method = RequestMethod.GET, produces = JSON_TYPE)
	public ResponseEntity<String> getBasicConfig() {
		CloudManagerAppSettings settings = applicationConfig.getCurrentSettings();

		JSONArray configValues = new JSONArray();

		configValues.put(createConfigPropertyObject(HOST_NAME_KEY, settings.getHostName(), HOST_NAME_DESCRIPTION));
		configValues.put(createConfigPropertyObject(USE_PROXY_KEY, settings.isUseProxy(), USE_PROXY_DESCRIPTION));
		configValues.put(createConfigPropertyObject(PROXY_HOST_KEY, settings.getProxyHost(), PROXY_HOST_DESCRIPTION));
		configValues.put(createConfigPropertyObject(PROXY_PORT_KEY, settings.getProxyPort(), PROXY_PORT_DESCRIPTION));
		configValues.put(createConfigPropertyObject(BYPASS_PROXY_REGEX_KEY, settings.getBypassProxyRegexp(),
				BYPASS_PROXY_REGEX_DESCRIPTION));

		JSONObject result = new JSONObject();
		result.put("config", configValues);
		return wrapResultObject(result);
	}

	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/config", method = RequestMethod.POST, consumes = FORM_TYPE, produces = JSON_TYPE)
	public ResponseEntity<String> setBasicConfig(@RequestBody MultiValueMap<String, String> formData) {
		CloudManagerAppConfigAdmin admin = applicationConfig.getAdminInterface(CloudManagerAppConfigAdmin.class);
		if (admin == null) {
			return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
		}

		// filter for valid keys only
		List<Map.Entry<String, List<String>>> keys = formData.entrySet().stream().filter(e -> VALID_CONFIG_KEYS.contains(e.getKey()) && e.getValue().size() == 1).collect(Collectors.toList());

		// not really performance optimized, but short code :-)
		keys.stream().filter(e -> HOST_NAME_KEY.equals(e.getKey())).findAny()
				.ifPresent(e -> admin.setHostName(e.getValue().get(0)));
		keys.stream().filter(e -> USE_PROXY_KEY.equals(e.getKey())).findAny()
				.ifPresent(e -> admin.setUseProxy(Boolean.valueOf(e.getValue().get(0))));
		keys.stream().filter(e -> PROXY_HOST_KEY.equals(e.getKey())).findAny()
				.ifPresent(e -> admin.setProxyHost(e.getValue().get(0)));
		keys.stream().filter(e -> BYPASS_PROXY_REGEX_KEY.equals(e.getKey())).findAny()
				.ifPresent(e -> admin.setBypassProxyRegex(e.getValue().get(0)));

		// extra block to parse and check proxy port
		Optional<String> s = keys.stream().filter(e -> PROXY_PORT_KEY.equals(e.getKey())).map(e -> e.getValue().get(0)).findAny();
		if (s.isPresent()) {
			try {
				int port = Integer.parseInt(s.get());
				if (port < 1 || port > 65535) {
					return createErrorObject(PROXY_PORT_KEY + " is invalid",
							new ConfigException(PROXY_PORT_KEY + " is invalid", PROXY_PORT_KEY));
				}
				admin.setProxyPort(port);
			}
			catch (NumberFormatException e) {
				return createErrorObject(PROXY_PORT_KEY + " is invalid",
						new ConfigException(PROXY_PORT_KEY + " is invalid", PROXY_PORT_KEY));
			}
		}

		try {
			admin.commit();
		}
		catch (ConfigException e) {
			return createErrorObject(e);
		}

		return getBasicConfig();
	}

}

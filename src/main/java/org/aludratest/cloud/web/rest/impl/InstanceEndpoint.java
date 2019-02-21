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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import org.aludratest.cloud.web.rest.AbstractRestController;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Endpoint for retrieving information about this ACM instance, e.g., the
 * instance name, if set via startup parameters. Accessible via
 * <code>/api/instance</code>.
 *
 * @author falbrech
 *
 */
@RestController
public class InstanceEndpoint extends AbstractRestController {

	private static final Log LOG = LogFactory.getLog(InstanceEndpoint.class);

	@Autowired
	private ApplicationArguments applicationArguments;

	@RequestMapping(value = "/api/instance", method = RequestMethod.GET, produces = JSON_TYPE)
	public ResponseEntity<String> getInstanceProperties() {
		JSONObject result = new JSONObject();

		List<String> names = applicationArguments.getOptionValues("acm.instance.name");
		if (names != null && !names.isEmpty()) {
			result.put("instanceName", names.get(0));
		}

		// get version from compiled properties file
		try (InputStream in = InstanceEndpoint.class.getClassLoader()
				.getResourceAsStream("META-INF/maven/org.aludratest/cloud-manager-web/pom.properties")) {
			if (in != null) {
				Properties p = new Properties();
				p.load(in);
				result.put("version", p.getProperty("version"));
			}
		} catch (IOException e) {
			// ignore - no version information in result
			LOG.warn("Could not parse Maven version information", e);
		}

		return wrapResultObject(result);
	}

}

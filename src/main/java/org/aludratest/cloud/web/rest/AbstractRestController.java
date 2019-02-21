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
package org.aludratest.cloud.web.rest;

import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Abstract base class for REST controllers. Provides some utility classes for a uniform handling of REST requests. Also,
 * subclasses can implement {@link #addLinks(RequestMapping, HttpServletRequest, Object)} to provide custom links for arbitrary
 * JSON objects which will be returned (this functionality must explicitly be invoked by calling
 * {@link #decorateLinks(JSONObject, HttpServletRequest, Object)} by the method creating a result JSON object).
 *
 * @author falbrech
 *
 */
public abstract class AbstractRestController {

	/**
	 * Constant for JSON content type.
	 */
	public static final String JSON_TYPE = "application/json";

	/**
	 * Constant for form data content type.
	 */
	public static final String FORM_TYPE = "application/x-www-form-urlencoded";

	private Log log;

	@Autowired
	private ApplicationContext applicationContext;

	/**
	 * Builds relationship API links for REST JSON objects. This function must return a JSON array where each entry is an object
	 * with the fields <code>"rel"</code> and <code>"href"</code>. The <code>href</code> value must contain the full URL to the
	 * target. You can use {@link #createLinkObject(HttpServletRequest, String, String)} to build such a JSON object. <br>
	 * The default implementation does nothing. Subclasses can override if they need to add links to objects returned by other
	 * classes.
	 *
	 * @param requestMapping
	 *            The mapping annotation which triggered the current endpoint to become active.
	 * @param request
	 *            Current HTTP servlet request.
	 * @param endpointContext
	 *            Context object which has been passed to {@link #decorateLinks(JSONObject, HttpServletRequest, Object)} by the
	 *            caller - may be <code>null</code>.
	 * @return JSON array with objects describing all relationship links.
	 */
	protected JSONArray addLinks(RequestMapping requestMapping, HttpServletRequest request, Object endpointContext) {
		// default implementation does nothing
		return new JSONArray();
	}

	/**
	 * Adds relationship links to the given JSON object. This method searches all beans which extend
	 * {@link AbstractRestController} and invokes their {@link #addLinks(RequestMapping, HttpServletRequest, Object)} method. The
	 * request mapping is determined from current caller stack.
	 *
	 * @param object
	 *            JSON object which may already have, or receive by this method, an array named "links" with all relationship
	 *            links. If no links are found for this object, the object will remain unchanged.
	 * @param request
	 *            Current HTTP servlet request to determine absolute link URLs.
	 * @param context
	 *            Custom object to be passed by the caller (may be <code>null</code>). Can help implementors for easier link
	 *            calculation (e.g. a group endpoint can pass the already determined resource group object).
	 */
	protected void decorateLinks(JSONObject object, HttpServletRequest request, Object context) {
		RequestMapping mapping = determineRequestMapping();
		if (mapping == null) {
			getLog().warn("Could not find RequestMapping annotation in current call stack. Cannot decorate object links.");
			return;
		}

		Map<String, AbstractRestController> restControllers = applicationContext.getBeansOfType(AbstractRestController.class);

		for (AbstractRestController controller : restControllers.values()) {
			JSONArray links = controller.addLinks(mapping, request, context);
			for (int i = 0; i < links.length(); i++) {
				object.append("links", links.getJSONObject(i));
			}
		}
	}

	// Developers note: The getLog() method should be final. But
	// as child classes will most probably use e.g. @PreAuthorize annotations, which
	// cause use of AOP proxies, they must not be final to allow the proxy to
	// override them.

	/**
	 * Returns the log to use to log REST connector specific information.
	 *
	 * @return The log to use to log REST connector specific information.
	 */
	protected Log getLog() {
		if (log == null) {
			log = LogFactory.getLog(getClass());
		}
		return log;
	}

	/**
	 * Wraps the given JSON object in a result object and builds a Response object. The response will contain a JSON object which
	 * contains a field with name <code>result</code> and the passed JSON object as value.
	 *
	 * @param result
	 *            JSON object to wrap in a result object.
	 *
	 * @return A ResponseEntity object with status <code>OK</code> and the wrapping object as content.
	 */
	protected static final ResponseEntity<String> wrapResultObject(JSONObject result) {
		JSONObject obj = new JSONObject();
		try {
			obj.put("result", result);
			return ResponseEntity.ok(obj.toString());
		}
		catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Wraps the given JSON object in a result object and builds a Response object with the given HTTP status. The response will
	 * contain a JSON object which contains a field with name <code>result</code> and the passed JSON object as value.
	 *
	 * @param result
	 *            JSON object to wrap in a result object.
	 * @param responseCode
	 *            HTTP status to use as status of the Response.
	 *
	 * @return A ResponseEntity object with the given HTTP status code and the wrapping object as content.
	 */
	protected static final ResponseEntity<String> wrapResultObject(JSONObject result, HttpStatus responseStatus) {
		JSONObject obj = new JSONObject();
		try {
			obj.put("result", result);
			return ResponseEntity.status(responseStatus).body(obj.toString());
		}
		catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Wraps the given JSON array in a result object and builds a Response object.
	 * The response will contain a JSON object which contains a field with name
	 * <code>result</code> and the passed JSON array as value.
	 *
	 * @param result
	 *            JSON array to wrap in a result object.
	 *
	 * @return A ResponseEntity object with status <code>OK</code> and the wrapping
	 *         object as content.
	 */
	protected static final ResponseEntity<String> wrapResultArray(JSONArray result) {
		JSONObject obj = new JSONObject();
		try {
			obj.put("result", result);
			return ResponseEntity.ok(obj.toString());
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates a Response carrying a standard JSON error object describing the given Throwable. The response will carry the status
	 * code <code>SC_BAD_REQUEST</code> (400).
	 *
	 * @param cause
	 *            Throwable to wrap in a JSON error object.
	 *
	 * @return ResponseEntity object carrying the JSON error object and with HTTP status code <code>SC_BAD_REQUEST</code> (400).
	 */
	protected static final ResponseEntity<String> createErrorObject(Throwable cause) {
		return createErrorObject(cause.getMessage(), cause, HttpServletResponse.SC_BAD_REQUEST);
	}

	/**
	 * Creates a Response carrying a standard JSON error object describing the given Throwable. The response will carry the given
	 * status code.
	 *
	 * @param cause
	 *            Throwable to wrap in a JSON error object.
	 * @param responseCode
	 *            HTTP status code to use for the Response object.
	 *
	 * @return ResponseEntity object carrying the JSON error object and with given HTTP status code.
	 */
	protected static final ResponseEntity<String> createErrorObject(Throwable cause, int responseCode) {
		return createErrorObject(cause.getMessage(), cause, responseCode);
	}

	/**
	 * Creates a Response carrying a standard JSON error object describing the given Throwable and providing the given error
	 * message. The response will carry the status code <code>SC_BAD_REQUEST</code> (400).
	 *
	 * @param message
	 *            Error message to set in the JSON error object.
	 * @param cause
	 *            Throwable to wrap in a JSON error object.
	 *
	 * @return ResponseEntity object carrying the JSON error object and with HTTP status code <code>SC_BAD_REQUEST</code> (400).
	 */
	protected static final ResponseEntity<String> createErrorObject(String message, Throwable cause) {
		return createErrorObject(message, cause, HttpServletResponse.SC_BAD_REQUEST);
	}

	/**
	 * Creates a Response carrying a standard JSON error object describing the given Throwable and providing the given error
	 * message. The response will carry the given HTTP status code.
	 *
	 * @param message
	 *            Error message to set in the JSON error object.
	 * @param cause
	 *            Throwable to wrap in a JSON error object.
	 * @param responseCode
	 *            HTTP status code to use for the Response object.
	 *
	 * @return ResponseEntity object carrying the JSON error object and with given HTTP status code.
	 */
	protected static final ResponseEntity<String> createErrorObject(String message, Throwable cause, int responseCode) {
		try {
			JSONObject error = new JSONObject();
			error.put("message", message);
			if (cause != null) {
				error.put("error", cause.getMessage());
				// TODO could be more verbose
				error.put("exceptionClass", cause.getClass().getName());
			}
			return ResponseEntity.status(responseCode).body(error.toString());
		}
		catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	protected static final JSONObject createLinkObject(HttpServletRequest request, String rel, String hrefPath) {
		JSONObject obj = new JSONObject();
		obj.put("rel", rel);
		obj.put("href", buildApiUrl(request, hrefPath));
		return obj;
	}

	protected static final JSONObject createConfigPropertyObject(String configKey, Object configValue,
			String description) {
		JSONObject obj = new JSONObject();
		obj.put("key", configKey);
		obj.put("value", configValue);
		obj.put("description", description);
		return obj;
	}

	protected static final String buildApiUrl(HttpServletRequest request, String path) {
		return getBaseUrl(request) + path;
	}

	private static String getBaseUrl(HttpServletRequest request) {
		try {
			URL url = new URL(request.getRequestURL().toString());
			return new StringBuilder(url.getProtocol()).append("://").append(url.getHost())
					.append(url.getPort() != -1 ? ":" + url.getPort() : "").toString();
		}
		catch (MalformedURLException e) {
			// should not occur!
			throw new UndeclaredThrowableException(e);
		}
	}

	private RequestMapping determineRequestMapping() {
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		RequestMapping mapping = null;

		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		if (cl == null) {
			cl = AbstractRestController.class.getClassLoader();
		}

		// always start with third element (skip this method and the decorateLinks() method)
		// and do not dive too deep
		for (int i = 2; mapping == null && i < 6 && i < stackTrace.length; i++) {
			try {
				Class<?> clazz = cl.loadClass(stackTrace[i].getClassName());
				for (Method m : clazz.getDeclaredMethods()) {
					if (m.getName().equals(stackTrace[i].getMethodName())
							&& (mapping = m.getAnnotation(RequestMapping.class)) != null) {
						break;
					}
				}
			}
			catch (Throwable t) {
				// ignore here
				continue;
			}
		}

		return mapping;
	}
}

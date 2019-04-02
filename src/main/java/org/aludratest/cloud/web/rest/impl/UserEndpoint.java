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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletResponse;

import org.aludratest.cloud.config.ConfigException;
import org.aludratest.cloud.module.ResourceModule;
import org.aludratest.cloud.module.ResourceModuleRegistry;
import org.aludratest.cloud.resource.ResourceType;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorization;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorizationConfig;
import org.aludratest.cloud.resource.user.ResourceTypeAuthorizationStore;
import org.aludratest.cloud.resource.user.SimpleResourceTypeAuthorization;
import org.aludratest.cloud.resource.user.SimpleResourceTypeAuthorizationConfig;
import org.aludratest.cloud.user.StoreException;
import org.aludratest.cloud.user.User;
import org.aludratest.cloud.user.UserDatabase;
import org.aludratest.cloud.user.admin.UserDatabaseRegistry;
import org.aludratest.cloud.web.rest.AbstractRestController;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for managing the users of the application's selected user database.
 *
 * @author falbrech
 *
 */
@RestController
public class UserEndpoint extends AbstractRestController {

	private UserDatabaseRegistry userDatabaseRegistry;

	private ResourceModuleRegistry resourceModuleRegistry;

	private ResourceTypeAuthorizationStore authorizationStore;

	@Autowired
	public UserEndpoint(UserDatabaseRegistry userDatabaseRegistry, ResourceModuleRegistry resourceModuleRegistry,
			ResourceTypeAuthorizationStore authorizationStore) {
		this.userDatabaseRegistry = userDatabaseRegistry;
		this.resourceModuleRegistry = resourceModuleRegistry;
		this.authorizationStore = authorizationStore;
	}

	/**
	 * Lists all users existing in the current user database.
	 *
	 * @return A JSON object listing all users in the current user database.
	 */
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/users", method = RequestMethod.GET, produces = JSON_TYPE)
	public ResponseEntity<String> getUsers() {
		UserDatabase users = userDatabaseRegistry.getSelectedUserDatabase();
		if (users == null) {
			return ResponseEntity.notFound().build();
		}

		JSONObject result = new JSONObject();
		result.put("isEditable", !users.isReadOnly());

		JSONArray arr = new JSONArray();

		try {
			Iterator<User> iter = users.getAllUsers(null);
			while (iter.hasNext()) {
				arr.put(getUserJSON(iter.next()));
			}

			result.put("users", arr);
			return wrapResultObject(result);
		}
		catch (StoreException e) {
			getLog().error("Could not retrieve users list", e);
			return createErrorObject(new RuntimeException("Could not retrieve users list."),
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Returns detailed information about a single user in the application's current user database.
	 *
	 * @param userName
	 *            User name to retrieve information about.
	 * @return A JSON object describing the user, or HTTP status 404 if no user with the given name could be found.
	 */
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/users/{userName}", method = RequestMethod.GET, produces = JSON_TYPE)
	public ResponseEntity<String> getUser(@PathVariable("userName") String userName) {
		UserDatabase users = userDatabaseRegistry.getSelectedUserDatabase();
		if (users == null) {
			return ResponseEntity.notFound().build();
		}

		JSONObject result = new JSONObject();

		try {
			User user = users.findUser(userName);
			if (user == null) {
				return ResponseEntity.notFound().build();
			}

			result.put("user", getUserJSON(user));
			return wrapResultObject(result);
		}
		catch (StoreException e) {
			getLog().error("Could not retrieve user", e);
			return createErrorObject(new RuntimeException("Could not retrieve user information."),
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Adds a new user to the application's current user database.
	 *
	 * @param userName
	 *            User name of the user to add.
	 * @return HTTP status 204 plus a JSON object describing the newly created user, or HTTP status 400 and a JSON error object
	 *         describing any errors, e.g. user name already exists. HTTP status 501 (NOT_IMPLEMENTED) if the current user
	 *         database does not support creating new users.
	 */
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/users", method = RequestMethod.PUT, consumes = FORM_TYPE, produces = JSON_TYPE)
	public ResponseEntity<String> addUser(@RequestParam("name") String userName) {
		JSONObject result = new JSONObject();
		UserDatabase users = userDatabaseRegistry.getSelectedUserDatabase();
		if (users == null) {
			return ResponseEntity.notFound().build();
		}

		if (users.isReadOnly()) {
			return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
		}

		if (StringUtils.isEmpty(userName)) {
			return createErrorObject(new ConfigException("The user name must not be empty", "name"));
		}

		try {
			User user = users.findUser(userName);
			if (user != null) {
				return createErrorObject(new ConfigException("A user with this name already exists.", "name"));
			}

			user = users.create(userName);
			result.put("user", getUserJSON(user));
			return wrapResultObject(result, HttpStatus.CREATED);
		}
		catch (StoreException e) {
			getLog().error("Could not query user database", e);
			return createErrorObject(new RuntimeException("Could not query user database"),
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/users/{userName}/isAdmin", method = RequestMethod.POST, consumes = FORM_TYPE, produces = JSON_TYPE)
	public ResponseEntity<String> setUserAdminFlag(@PathVariable("userName") String userName,
			@RequestParam("isAdmin") boolean isAdmin) {
		UserDatabase users = userDatabaseRegistry.getSelectedUserDatabase();
		if (users == null) {
			return ResponseEntity.notFound().build();
		}

		if (!users.canChangeAdminFlag()) {
			return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
		}

		try {
			User user = users.findUser(userName);
			if (user == null) {
				return ResponseEntity.notFound().build();
			}

			users.setAdminFlag(user, isAdmin);
			return getUser(userName);
		} catch (StoreException e) {
			getLog().error("Could not update user database", e);
			return createErrorObject(new RuntimeException("Could not update user database"),
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}


	/**
	 * Deletes a user from the application's current user database.
	 *
	 * @param userName
	 *            User name of the user to delete.
	 *
	 * @return An empty response with HTTP status 204 if the deletion was successful, or HTTP status 404 if no user with the given
	 *         user name was found. HTTP status 501 indicates that the current user database does not support modification and
	 *         deletion of users.
	 */
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/users/{userName}", method = RequestMethod.DELETE, produces = JSON_TYPE)
	public ResponseEntity<String> deleteUser(@PathVariable("userName") String userName) {
		UserDatabase users = userDatabaseRegistry.getSelectedUserDatabase();
		if (users == null) {
			return ResponseEntity.notFound().build();
		}
		if (users.isReadOnly()) {
			return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
		}

		try {
			User user = users.findUser(userName);
			if (user == null) {
				return ResponseEntity.notFound().build();
			}

			users.delete(user);
			return ResponseEntity.noContent().build();
		}
		catch (StoreException e) {
			getLog().error("Could not query user database", e);
			return createErrorObject(new RuntimeException("Could not query user database"),
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/users/{userName}/attributes", method = RequestMethod.POST, consumes = FORM_TYPE, produces = JSON_TYPE)
	public ResponseEntity<String> modifyUserAttributes(@PathVariable("userName") String userName,
			MultiValueMap<String, String> attributes) {
		UserDatabase users = userDatabaseRegistry.getSelectedUserDatabase();
		if (users == null) {
			return ResponseEntity.notFound().build();
		}
		if (users.isReadOnly()) {
			return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
		}

		try {
			User user = users.findUser(userName);
			if (user == null) {
				return ResponseEntity.notFound().build();
			}

			// check if every attribute is supported, before starting applying them
			Optional<String> unsupportedKey = attributes.keySet().stream().filter(k -> !users.supportsUserAttribute(k)).findAny();
			if (unsupportedKey.isPresent()) {
				return createErrorObject(
						new ConfigException("Unsupported user attribute: " + unsupportedKey.get(), unsupportedKey.get()));
			}

			for (Map.Entry<String, List<String>> attr : attributes.entrySet()) {
				// always use LAST element of list
				if (!attr.getValue().isEmpty()) {
					users.modifyUserAttribute(user, attr.getKey(), attr.getValue().get(attr.getValue().size() - 1));
				}
			}

			// refresh user
			user = users.findUser(userName);
			return wrapResultObject(getUserJSON(user));
		}
		catch (StoreException e) {
			getLog().error("Could not query or modify user database", e);
			return createErrorObject(new RuntimeException("Could not query or modify user database"),
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/users/{userName}/password", method = RequestMethod.POST, consumes = FORM_TYPE, produces = JSON_TYPE)
	public ResponseEntity<String> modifyUserPassword(@PathVariable("userName") String userName,
			@RequestParam("password") String password) {
		UserDatabase users = userDatabaseRegistry.getSelectedUserDatabase();
		if (users == null) {
			return ResponseEntity.notFound().build();
		}
		if (users.isReadOnly()) {
			return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
		}

		try {
			User user = users.findUser(userName);
			if (user == null) {
				return ResponseEntity.notFound().build();
			}

			users.changePassword(user, password);

			// just OK
			return ResponseEntity.ok().build();
		}
		catch (StoreException e) {
			getLog().error("Could not query or modify user database", e);
			return createErrorObject(new RuntimeException("Could not query or modify user database"),
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/users/{userName}/authorizations", method = RequestMethod.GET, produces = JSON_TYPE)
	public ResponseEntity<String> getResourceAuthorizations(@PathVariable("userName") String userName) {
		UserDatabase users = userDatabaseRegistry.getSelectedUserDatabase();
		if (users == null) {
			return ResponseEntity.notFound().build();
		}
		if (users.isReadOnly()) {
			return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
		}

		try {
			User user = users.findUser(userName);
			if (user == null) {
				return ResponseEntity.notFound().build();
			}

			// bad algorithm, must always load everything, just for this single user
			JSONObject result = new JSONObject();
			for (ResourceModule m : resourceModuleRegistry.getAllResourceModules()) {
				ResourceTypeAuthorizationConfig authConfig = authorizationStore
						.loadResourceTypeAuthorizations(m.getResourceType());
				if (authConfig != null) {
					ResourceTypeAuthorization auth = authConfig.getResourceTypeAuthorizationForUser(user);
					if (auth != null) {
						JSONObject value = new JSONObject();
						value.put("maxResources", auth.getMaxResources());
						value.put("niceLevel", auth.getNiceLevel());
						result.put(m.getResourceType().getName(), value);
					}
				}
			}

			return wrapResultObject(result);
		} catch (StoreException e) {
			getLog().error("Could not query user database", e);
			return createErrorObject(new RuntimeException("Could not query user database"),
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/users/{userName}/authorizations", method = RequestMethod.POST, consumes = JSON_TYPE, produces = JSON_TYPE)
	public ResponseEntity<String> setResourceAuthorizations(@PathVariable("userName") String userName,
			@RequestBody Map<String, ResourceTypeAuthorizationDto> resourceAuthorizations) {
		UserDatabase users = userDatabaseRegistry.getSelectedUserDatabase();
		if (users == null) {
			return ResponseEntity.notFound().build();
		}

		User user;
		try {
			user = users.findUser(userName);
			if (user == null) {
				return ResponseEntity.notFound().build();
			}
		} catch (StoreException e) {
			return createErrorObject(e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}

		// be very strict about resource types, check them first
		if (resourceAuthorizations.keySet().stream()
				.anyMatch(rt -> resourceModuleRegistry.getResourceModule(rt) == null)) {
			return ResponseEntity.badRequest().build();
		}

		try {
			for (Map.Entry<String, ResourceTypeAuthorizationDto> typeAuth : resourceAuthorizations.entrySet()) {
				ResourceType resType = resourceModuleRegistry.getResourceModule(typeAuth.getKey())
						.getResourceType();
				ResourceTypeAuthorizationConfig authConfig = authorizationStore.loadResourceTypeAuthorizations(resType);
				SimpleResourceTypeAuthorizationConfig newConfig = (null!= authConfig)? new SimpleResourceTypeAuthorizationConfig(authConfig):new SimpleResourceTypeAuthorizationConfig();
				SimpleResourceTypeAuthorization newAuth = new SimpleResourceTypeAuthorization(
						typeAuth.getValue().getMaxResources(), typeAuth.getValue().getNiceLevel());

				if (newConfig.getResourceTypeAuthorizationForUser(user) == null) {
					newConfig.addUser(user, newAuth);
				} else {
					newConfig.editUserAuthorization(user, newAuth);
				}
				authorizationStore.saveResourceTypeAuthorizations(resType, newConfig);
			}
		} catch (StoreException e) {
			getLog().error("Could not load or save resource authorization database", e);
			return createErrorObject(new RuntimeException("Could not update resource authorization database"),
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}

		return getResourceAuthorizations(userName);
	}

	private JSONObject getUserJSON(User user) throws JSONException {
		JSONObject u = new JSONObject();
		u.put("name", user.getName());
		u.put("source", user.getSource());
		u.put("isAdmin", user.isAdmin());

		String[] attrs = user.getDefinedUserAttributes();
		if (attrs.length > 0) {
			JSONObject a = new JSONObject();
			for (String attr : attrs) {
				a.put(attr, user.getUserAttribute(attr));
			}
			u.put("customAttributes", a);
		}

		return u;
	}

}

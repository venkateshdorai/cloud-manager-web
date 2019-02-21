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
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.websocket.server.PathParam;

import org.aludratest.cloud.config.ConfigException;
import org.aludratest.cloud.config.Configurable;
import org.aludratest.cloud.resourcegroup.AuthorizingResourceGroup;
import org.aludratest.cloud.resourcegroup.AuthorizingResourceGroupAdmin;
import org.aludratest.cloud.resourcegroup.ResourceGroup;
import org.aludratest.cloud.resourcegroup.ResourceGroupManager;
import org.aludratest.cloud.user.StoreException;
import org.aludratest.cloud.user.User;
import org.aludratest.cloud.user.admin.UserDatabaseRegistry;
import org.aludratest.cloud.web.rest.AbstractRestController;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthorizingGroupEndpoint extends AbstractRestController {

	// FIXME annotations for access control

	private static final Log LOG = LogFactory.getLog(AuthorizingGroupEndpoint.class);

	private static final String LIMIT_USERS_KEY = "limitUsers";

	private static final String LIMIT_USERS_DESCRIPTION = "Determines if the access to this group is limited to the users being configured to have access.";

	private ResourceGroupManager groupManager;

	private UserDatabaseRegistry userDatabaseRegistry;

	@Autowired
	public AuthorizingGroupEndpoint(ResourceGroupManager groupManager, UserDatabaseRegistry userDatabaseRegistry) {
		this.groupManager = groupManager;
		this.userDatabaseRegistry = userDatabaseRegistry;
	}

	@Override
	protected JSONArray addLinks(RequestMapping requestMapping, HttpServletRequest request, Object endpointContext) {
		// add /users link for AuthorizingResourceGroups
		if (endpointContext instanceof Object[]) {
			Object[] values = (Object[]) endpointContext;
			if (values.length != 2 || !(values[0] instanceof AuthorizingResourceGroup) || !(values[1] instanceof Number)) {
				return super.addLinks(requestMapping, request, endpointContext);
			}

			String usersLink = "/api/groups/" + values[1] + "/users";
			JSONArray array = new JSONArray();
			array.put(createLinkObject(request, "users", usersLink));
			return array;
		}
		return super.addLinks(requestMapping, request, endpointContext);
	}

	/**
	 * Lists all users which are allowed to access the resources of a given resource group. If the given resource group is not
	 * found or no resource group which is capable of setting the "limit users" flag, HTTP status 404 is returned. If the
	 * "limit users" flag is not active for the resource group, an empty array is contained in the result object.
	 *
	 * @param groupId
	 *            Registration ID of the resource group in the application's resource group manager.
	 *
	 * @return JSON response listing all users having access to the resources of the resource group, an empty list when the
	 *         "limit users" flag is not active, or HTTP status 404 if no group with the given registration was found, or the
	 *         group does not support the "limit users" flag.
	 */
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/groups/{groupId}/users", method = RequestMethod.GET, produces = JSON_TYPE)
	public ResponseEntity<String> getUsers(@PathVariable(name = "groupId", required = true) int groupId) {
		return getUsers(groupId, HttpStatus.OK);
	}

	private ResponseEntity<String> getUsers(int groupId, HttpStatus returnStatus) {
		ResourceGroup group = groupManager.getResourceGroup(groupId);
		if (group == null || !(group instanceof Configurable) || !(group instanceof AuthorizingResourceGroup)) {
			return ResponseEntity.notFound().build();
		}

		AuthorizingResourceGroupAdmin admin = ((Configurable) group).getAdminInterface(AuthorizingResourceGroupAdmin.class);
		if (admin == null) {
			return ResponseEntity.notFound().build();
		}

		List<User> users;
		if (!((AuthorizingResourceGroup) group).isLimitingUsers()) {
			users = Collections.emptyList();
		}
		else {
			try {
				users = admin.getConfiguredAuthorizedUsers();
			}
			catch (StoreException e) {
				LOG.error("Exception when querying user database", e);
				// do not reveal exception details to client
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
			}
		}

		JSONObject result = new JSONObject();
		JSONArray array = new JSONArray();

		for (User user : users) {
			JSONObject o = new JSONObject();
			o.put("source", user.getSource());
			o.put("name", user.getName());
			array.put(o);
		}

		result.put("users", array);
		return wrapResultObject(result, returnStatus);
	}

	/**
	 * Adds a user to the list of users having access to the resources of the given
	 * resource group. The <code>limitUsers</code> flag of the group is being set to
	 * <code>true</code> automatically, if not already set.
	 *
	 * @param groupId
	 *            Registration ID of the resource group in the application's
	 *            resource group manager.
	 *
	 * @param user
	 *            Name of the user to add. Must match an existing user name in the
	 *            application's current user database.
	 * @return The new list of users having access to the resources of the given
	 *         resource group, or HTTP status 404 if no user with the given user
	 *         name was found, or the group does not support the "limit users" flag.
	 */
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/groups/{groupId}/users", method = RequestMethod.PUT, consumes = FORM_TYPE, produces = JSON_TYPE)
	public ResponseEntity<String> addUser(@PathVariable(name = "groupId", required = true) int groupId,
			@RequestParam("user") String user) throws JSONException {
		return doUserAction(groupId, user, false);
	}

	/**
	 * Removes a user from the list of users having access to the resources of the given resource group.
	 *
	 * @param groupId
	 *            Registration ID of the resource group in the application's resource group manager.
	 *
	 * @param user
	 *            Name of the user to remove. Must match an existing user name in the application's current user database.
	 * @return HTTP status 204 (NO_CONTENT) if deletion was successful, or HTTP status 404 if no user with the given user name was
	 *         found, or the group does not support the "limit users" flag.
	 */
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/groups/{groupId}/users/{user}", method = RequestMethod.DELETE)
	public ResponseEntity<String> removeUser(@PathVariable(name = "groupId", required = true) int groupId,
			@PathParam("user") String user) {
		return doUserAction(groupId, user, true);
	}

	/**
	 * Sets the whole list of users having access to the resources of the given
	 * resource group at once. <br>
	 * Contrary to {@link #addUser(int, String)}, this method does <b>not</b>
	 * automatically update the <code>limitUsers</code> flag of the group
	 * configuration.
	 *
	 * @param groupId
	 *            Registration ID of the resource group in the application's
	 *            resource group manager.
	 * @param userNames
	 *            Array of user names who shall make up the list.
	 * @return The new list of users having access to the resources of the given
	 *         resource group, or HTTP status 404 if, for <b>any</b> user in the
	 *         array, no user with the given user name was found, or the group does
	 *         not support the "limit users" flag.
	 */
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/groups/{groupId}/users", method = RequestMethod.POST, consumes = JSON_TYPE, produces = JSON_TYPE)
	public ResponseEntity<String> setUsers(@PathVariable(name = "groupId", required = true) int groupId,
			@RequestBody(required = true) String[] userNames) {
		ResourceGroup group = groupManager.getResourceGroup(groupId);
		if (group == null || !(group instanceof Configurable) || !(group instanceof AuthorizingResourceGroup)) {
			return ResponseEntity.notFound().build();
		}

		AuthorizingResourceGroupAdmin admin = ((Configurable) group)
				.getAdminInterface(AuthorizingResourceGroupAdmin.class);
		if (admin == null) {
			return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
		}

		List<User> oldUsers;
		try {
			oldUsers = admin.getConfiguredAuthorizedUsers();
		} catch (StoreException e) {
			LOG.error("Exception when querying user database", e);
			// do not reveal exception details to client
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}

		for (String userName : userNames) {
			// user must also exist
			User userObject;
			try {
				userObject = userDatabaseRegistry.getSelectedUserDatabase().findUser(userName);
			} catch (StoreException e) {
				LOG.error("Exception when querying user database", e);
				// do not reveal exception details to client
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
			}
			if (userObject == null) {
				return createErrorObject(
						new IllegalArgumentException("User " + userName + " not found in current user database."));
			}

			if (!oldUsers.stream().filter(u -> u.getName().equals(userName)).findAny().isPresent()) {
				admin.addAuthorizedUser(userObject);
			}
		}

		// now, remove the ones no longer wanted
		List<String> newUsers = Arrays.asList(userNames);
		for (User user : oldUsers) {
			if (!newUsers.contains(user.getName())) {
				admin.removeAuthorizedUser(user);
			}
		}

		try {
			admin.commit();
			return getUsers(groupId, HttpStatus.CREATED);
		} catch (ConfigException e) {
			return createErrorObject(e);
		}
	}

	/**
	 * Retrieves all user authorization specific configuration elements for the given resource group. <br>
	 * Currently, this only contains the <code>limitUsers</code> flag, indicating if limiting the access to this group shall be
	 * limited to a list users or not. To specify the list, use the endpoints of {@link #addUser(int, String)} and
	 * {@link #removeUser(int, String)}. To view the list, use the endpoint of {@link #getUsers(int)}.
	 *
	 */
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/groups/{groupId}/config/users", method = RequestMethod.GET)
	public ResponseEntity<String> getAuthorizationConfig(@PathVariable(name = "groupId", required = true) int groupId) {
		ResourceGroup group = groupManager.getResourceGroup(groupId);
		if (group == null || !(group instanceof Configurable) || !(group instanceof AuthorizingResourceGroup)) {
			return ResponseEntity.notFound().build();
		}

		JSONArray config = new JSONArray();
		config.put(createConfigPropertyObject(LIMIT_USERS_KEY, ((AuthorizingResourceGroup) group).isLimitingUsers(),
				LIMIT_USERS_DESCRIPTION));
		JSONObject result = new JSONObject();
		result.put("config", config);

		return wrapResultObject(result);
	}

	/**
	 * Sets some or all user authorization specific configuration elements for the
	 * given resource group. <br>
	 * Currently, this only includes the <code>limitUsers</code> flag, indicating if
	 * limiting the access to this group shall be limited to a list users or not. To
	 * specify the list, use the endpoints of {@link #addUser(int, String)} and
	 * {@link #removeUser(int, String)}.
	 *
	 * @param groupId
	 *            Registration ID of the resource group in the application's
	 *            resource group manager.
	 * @param limitUsers
	 *            New value for the <code>limitUsers</code> flag.
	 * @return HTTP status 200 if updating the configuration was successful,
	 *         including a JSON representation of the new configuration. HTTP status
	 *         404 if the group with the given ID does not exist.
	 */
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/groups/{groupId}/config/users", method = RequestMethod.POST, consumes = FORM_TYPE, produces = JSON_TYPE)
	public ResponseEntity<String> setAuthorizationConfig(@PathVariable(name = "groupId", required = true) int groupId,
			@RequestParam(name = LIMIT_USERS_KEY, required = false) Boolean limitUsers) {
		ResourceGroup group = groupManager.getResourceGroup(groupId);
		if (group == null || !(group instanceof Configurable) || !(group instanceof AuthorizingResourceGroup)) {
			return ResponseEntity.notFound().build();
		}

		AuthorizingResourceGroupAdmin admin = ((Configurable) group).getAdminInterface(AuthorizingResourceGroupAdmin.class);
		if (admin == null) {
			return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
		}

		if (limitUsers != null) {
			admin.setLimitingUsers(limitUsers.booleanValue());
			try {
				admin.commit();
			} catch (ConfigException e) {
				return createErrorObject(e);
			}
		}

		return getAuthorizationConfig(groupId);
	}

	private ResponseEntity<String> doUserAction(int groupId, String user, boolean delete) throws JSONException {
		ResourceGroup group = groupManager.getResourceGroup(groupId);
		if (group == null || !(group instanceof Configurable) || !(group instanceof AuthorizingResourceGroup)) {
			return ResponseEntity.notFound().build();
		}

		// user must also exist
		User userObject;
		try {
			userObject = userDatabaseRegistry.getSelectedUserDatabase().findUser(user);
		}
		catch (StoreException e) {
			LOG.error("Exception when querying user database", e);
			// do not reveal exception details to client
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}

		if (userObject == null) {
			return createErrorObject(new IllegalArgumentException("User " + user + " not found in current user database."));
		}

		AuthorizingResourceGroupAdmin admin = ((Configurable) group).getAdminInterface(AuthorizingResourceGroupAdmin.class);
		if (admin == null) {
			return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
		}

		if (delete) {
			admin.removeAuthorizedUser(userObject);
		}
		else {
			admin.setLimitingUsers(true);
			admin.addAuthorizedUser(userObject);
		}

		try {
			admin.commit();
			if (delete) {
				return ResponseEntity.noContent().build();
			}
			else {
				return getUsers(groupId, HttpStatus.CREATED);
			}
		}
		catch (ConfigException e) {
			return createErrorObject(e);
		}

	}
}

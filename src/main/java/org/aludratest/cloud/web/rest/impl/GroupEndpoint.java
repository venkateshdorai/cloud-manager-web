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

import javax.servlet.http.HttpServletRequest;

import org.aludratest.cloud.config.ConfigException;
import org.aludratest.cloud.config.Configurable;
import org.aludratest.cloud.module.ResourceModule;
import org.aludratest.cloud.module.ResourceModuleRegistry;
import org.aludratest.cloud.request.ResourceRequest;
import org.aludratest.cloud.resource.Resource;
import org.aludratest.cloud.resource.ResourceStateHolder;
import org.aludratest.cloud.resource.writer.JSONResourceWriter;
import org.aludratest.cloud.resourcegroup.ResourceGroup;
import org.aludratest.cloud.resourcegroup.ResourceGroupManager;
import org.aludratest.cloud.resourcegroup.ResourceGroupManagerAdmin;
import org.aludratest.cloud.web.rest.AbstractRestController;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for access to Resource Group Manager functions.
 *
 * @author falbrech
 *
 */
@RestController
public class GroupEndpoint extends AbstractRestController {

	@Autowired
	private ResourceModuleRegistry resourceModuleRegistry;

	@Autowired
	private ResourceGroupManager groupManager;

	@Autowired
	private ResourceRequestMapper resourceRequestMapper;

	/**
	 * Returns a JSON object enumerating all resource groups registered in the application's current resource group manager.
	 *
	 * @return A JSON object enumerating all resource groups registered in the application's current resource group manager.
	 */
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/groups", method = RequestMethod.GET, produces = JSON_TYPE)
	public ResponseEntity<String> getAllGroups(HttpServletRequest request) {
		JSONObject result = new JSONObject();
		JSONArray arr = new JSONArray();

		for (int groupId : groupManager.getAllResourceGroupIds()) {
			ResourceGroup group = groupManager.getResourceGroup(groupId);

			JSONObject obj = new JSONObject();
			obj.put("id", groupId);
			obj.put("name", groupManager.getResourceGroupName(groupId));
			obj.put("type", group.getResourceType().getName());
			obj.put("resourceCount", group.getResourceCollection().getResourceCount());
			// links as array (Restful Objects standard)
			obj.append("links", createLinkObject(request, "self", "/api/groups/" + groupId));
			decorateLinks(obj, request, buildLinkContextObject(group, groupId));
			arr.put(obj);
		}

		result.put("groups", arr);
		return wrapResultObject(result);
	}

	/**
	 * Returns a JSON object describing the given resource group and its resources.
	 *
	 * @param groupId
	 *            Registration ID of the resource group in the application's resource group manager.
	 * @param request
	 *            The HTTP request for link generation.
	 *
	 * @return A JSON object describing the given resource group and its resources.
	 */
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/groups/{groupId}", method = RequestMethod.GET, produces = JSON_TYPE)
	public ResponseEntity<String> getGroup(@PathVariable(name = "groupId", required = true) int groupId,
			HttpServletRequest request) {
		return getGroup(groupId, request, HttpStatus.OK);
	}

	private ResponseEntity<String> getGroup(int groupId, HttpServletRequest request, HttpStatus returnStatus) {
		ResourceGroup group = groupManager.getResourceGroup(groupId);
		if (group == null) {
			return ResponseEntity.notFound().build();
		}

		JSONObject result = new JSONObject();
		result.put("id", groupId);
		result.put("name", groupManager.getResourceGroupName(groupId));
		result.put("type", group.getResourceType().getName());
		result.put("resourceCount", group.getResourceCollection().getResourceCount());

		JSONArray resources = new JSONArray();

		// find resource module for resource type
		ResourceModule module = resourceModuleRegistry.getResourceModule(group.getResourceType());
		JSONResourceWriter writer = module == null ? null
				: module.getResourceWriterFactory().getResourceWriter(JSONResourceWriter.class);

		for (ResourceStateHolder rsh : group.getResourceCollection()) {
			resources.put(buildResourceObject(rsh, writer));
		}

		result.put("resources", resources);

		result.append("links", createLinkObject(request, "self", "/api/groups/" + groupId));
		decorateLinks(result, request, buildLinkContextObject(group, groupId));

		return wrapResultObject(result, returnStatus);
	}

	private JSONObject buildResourceObject(ResourceStateHolder rsh, JSONResourceWriter writer) {
		JSONObject resObj = new JSONObject();
		if (writer != null && (rsh instanceof Resource) && writer.canWrite((Resource) rsh)) {
			resObj = writer.writeToJSON((Resource) rsh);
		}

		resObj.put("state", rsh.getState().toString());
		resObj.put("label", rsh.toString());

		// render some infos about the request, if present
		if (rsh instanceof Resource) {
			ResourceRequest request = resourceRequestMapper.getRequestFor((Resource) rsh);
			if (request != null) {
				JSONObject reqObj = new JSONObject();
				reqObj.put("user", request.getRequestingUser().getName());
				reqObj.putOpt("jobName", request.getJobName());

				// build attributes object
				JSONObject attrObj = new JSONObject(request.getCustomAttributes());
				if (!attrObj.keySet().isEmpty()) {
					reqObj.put("attributes", attrObj);
				}
				resObj.put("request", reqObj);
			}
		}

		return resObj;
	}

	/**
	 * Creates a new resource group in the application's resource group manager.
	 *
	 * @param name
	 *            Name of the new resource group.
	 * @param type
	 *            Type of resources to be managed by the new resource group.
	 * @param request
	 *            The HTTP request for link generation.
	 *
	 * @return A JSON object describing the new resource group, including its registration ID, as used by {@link #getGroup(int)}
	 */
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/groups", method = RequestMethod.PUT, consumes = FORM_TYPE, produces = JSON_TYPE)
	public ResponseEntity<String> createGroup(@RequestParam("name") String name, @RequestParam("type") String type,
			HttpServletRequest request) {
		ResourceGroupManagerAdmin admin = getGroupManagerConfigurationAdmin("create group");
		if (admin == null) {
			return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
		}

		// check that type exists
		ResourceModule module = null;
		for (ResourceModule m : resourceModuleRegistry.getAllResourceModules()) {
			if (m.getResourceType().getName().equals(type)) {
				module = m;
			}
		}

		if (module == null) {
			return createErrorObject(new IllegalArgumentException("Unknown resource type: " + type));
		}

		try {
			int groupId = admin.createResourceGroup(module.getResourceType(), name);
			admin.commit();
			return getGroup(groupId, request, HttpStatus.CREATED);
		}
		catch (ConfigException e) {
			return createErrorObject(e);
		}
	}

	/**
	 * Renames the given resource group.
	 *
	 * @param groupId
	 *            Registration ID of the resource group in the application's resource group manager.
	 * @param name
	 *            New name for the resource group.
	 * @param request
	 *            The HTTP request for link generation.
	 *
	 * @return A JSON object describing the resource group, including its new name, or an HTTP Status 404 if the group with the
	 *         given registration ID could not be found in the application's resource group manager.
	 */
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/groups/{groupId}", method = RequestMethod.POST, consumes = FORM_TYPE, produces = JSON_TYPE)
	public ResponseEntity<String> renameGroup(@PathVariable(name = "groupId", required = true) int groupId,
			@RequestParam("name") String name,
			HttpServletRequest request) {
		ResourceGroupManagerAdmin admin = getGroupManagerConfigurationAdmin("rename group");
		if (admin == null) {
			return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
		}

		ResourceGroup group = groupManager.getResourceGroup(groupId);
		if (group == null) {
			return ResponseEntity.notFound().build();
		}

		try {
			admin.renameResourceGroup(groupId, name);
			admin.commit();
			// TODO this will also send all resources. Maybe a little overhead for this task.
			return getGroup(groupId, request, HttpStatus.OK);
		}
		catch (ConfigException e) {
			return createErrorObject(e);
		}
	}

	/**
	 * Deletes the given group from the application's resource group manager.
	 *
	 * @param groupId
	 *            Registration ID of the resource group in the application's resource group manager.
	 * @return An empty response with HTTP status 204 if deletion was successful. HTTP status 404 if no group with the given
	 *         registration ID could not be found in the application's resource group manager. A JSON error object if the group
	 *         could not be deleted, including HTTP status 400.
	 */
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@RequestMapping(value = "/api/groups/{groupId}", method = RequestMethod.DELETE, produces = JSON_TYPE)
	public ResponseEntity<String> deleteGroup(@PathVariable(name = "groupId", required = true) int groupId) {
		ResourceGroupManagerAdmin admin = getGroupManagerConfigurationAdmin("delete group");
		if (admin == null) {
			return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
		}

		ResourceGroup group = groupManager.getResourceGroup(groupId);
		if (group == null) {
			return ResponseEntity.notFound().build();
		}

		try {
			admin.deleteResourceGroup(groupId);
			admin.commit();
			return ResponseEntity.noContent().build();
		}
		catch (ConfigException e) {
			return createErrorObject(e);
		}
	}


	private ResourceGroupManagerAdmin getGroupManagerConfigurationAdmin(String actionToPerform) {
		// resource group manager must be configurable for this
		if (!(groupManager instanceof Configurable)) {
			getLog().warn("Resource group manager is not configurable, cannot " + actionToPerform
					+ ". Returning 501 for REST request.");
			return null;
		}
		ResourceGroupManagerAdmin admin = ((Configurable) groupManager).getAdminInterface(ResourceGroupManagerAdmin.class);
		if (admin == null) {
			getLog().warn("Resource group manager does not provide ResourceGroupManagerAdmin interface, cannot " + actionToPerform
					+ ". Returning 501 for REST request.");
			return null;
		}

		return admin;
	}

	private Object[] buildLinkContextObject(ResourceGroup group, int groupId) {
		return new Object[] { group, Integer.valueOf(groupId) };
	}

}

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

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.aludratest.cloud.manager.ManagedResourceRequest;
import org.aludratest.cloud.manager.ResourceManager;
import org.aludratest.cloud.resource.Resource;
import org.aludratest.cloud.resource.ResourceState;
import org.aludratest.cloud.resource.ResourceStateHolder;
import org.aludratest.cloud.resourcegroup.ResourceGroup;
import org.aludratest.cloud.resourcegroup.ResourceGroupManager;
import org.aludratest.cloud.web.rest.AbstractRestController;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MonitoringEndpoint extends AbstractRestController {

	private static final DateTimeFormatter jsonFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

	private ResourceManager resourceManager;

	private ResourceGroupManager groupManager;

	@Autowired
	public MonitoringEndpoint(ResourceManager resourceManager, ResourceGroupManager groupManager) {
		this.resourceManager = resourceManager;
		this.groupManager = groupManager;
	}

	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@GetMapping(value = "/api/monitoring/requests", produces = JSON_TYPE)
	public ResponseEntity<String> getManagedRequests() {
		Iterator<? extends ManagedResourceRequest> iter = resourceManager.getManagedRequests();
		JSONArray requests = new JSONArray();
		iter.forEachRemaining(r -> requests.put(toJSONObject(r)));

		return wrapResultArray(requests);
	}

	private static JSONObject toJSONObject(ManagedResourceRequest request) {
		JSONObject result = new JSONObject();
		result.put("state", request.getState());
		result.put("creationTimestamp", toJSONTimestamp(request.getCreationTimestamp()));
		result.put("idleTimeMs", request.getIdleTimeMs());
		Resource resource = getSafeResource(request);
		if (resource != null) {
			result.put("assignedResource", resource.toString());
		}

		JSONObject rq = new JSONObject();
		rq.put("userName", request.getRequest().getRequestingUser().getName());
		rq.put("resourceType", request.getRequest().getResourceType().getName());
		rq.put("jobName", request.getRequest().getJobName());
		rq.put("niceLevel", request.getRequest().getNiceLevel());
		rq.put("customAttributes", new JSONObject(request.getRequest().getCustomAttributes()));

		result.put("request", rq);

		return result;
	}

	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@GetMapping(value = "/api/monitoring/stats", produces = JSON_TYPE)
	public ResponseEntity<String> getStats() {
		int totalRequests = 0;
		int waitingRequests = 0;
		int resourcesUsed = 0;
		int availableResources = 0;
		long waitTime = 0;

		Iterator<? extends ManagedResourceRequest> iter = resourceManager.getManagedRequests();
		while (iter.hasNext()) {
			ManagedResourceRequest request = iter.next();
			totalRequests++;
			if (request.getState() == ManagedResourceRequest.State.WAITING) {
				waitingRequests++;
			}
			if (request.getState() == ManagedResourceRequest.State.WORKING) {
				resourcesUsed++;
				waitTime += request.getWaitTimeMs();
			}
		}

		if (resourcesUsed > 0) {
			waitTime /= resourcesUsed;
		}

		for (int groupId : groupManager.getAllResourceGroupIds()) {
			ResourceGroup group = groupManager.getResourceGroup(groupId);
			for (ResourceStateHolder res : group.getResourceCollection()) {
				if (res.getState() == ResourceState.READY || res.getState() == ResourceState.IN_USE) {
					availableResources++;
				}
			}
		}

		JSONObject result = new JSONObject();
		result.put("availableResources", availableResources);
		result.put("totalRequests", totalRequests);
		result.put("resourcesUsed", resourcesUsed);
		result.put("averageWaitTimeMs", waitTime);
		result.put("waitingRequests", waitingRequests);

		return wrapResultObject(result);
	}

	private static String toJSONTimestamp(ZonedDateTime timestamp) {
		return jsonFormat.format(timestamp.withZoneSameInstant(ZoneOffset.UTC));
	}

	private static Resource getSafeResource(ManagedResourceRequest request) {
		try {
			return request.getResourceFuture().get(10, TimeUnit.MILLISECONDS);
		} catch (TimeoutException | InterruptedException | ExecutionException e) {
			return null;
		}
	}
}

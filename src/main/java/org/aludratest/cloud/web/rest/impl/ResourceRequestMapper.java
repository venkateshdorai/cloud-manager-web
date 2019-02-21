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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.aludratest.cloud.event.ManagedResourceRequestEvent;
import org.aludratest.cloud.event.ManagedResourceRequestStateChangedEvent;
import org.aludratest.cloud.request.ResourceRequest;
import org.aludratest.cloud.resource.Resource;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ResourceRequestMapper {

	private Map<Resource, ResourceRequest> resourceRequestMap = new ConcurrentHashMap<>();

	@EventListener
	public void handleManagedResourceStateChanged(ManagedResourceRequestStateChangedEvent event) {
		switch (event.getNewState()) {
			case FINISHED:
			case ORPHANED:
				resourceRequestMap.remove(getSafeResource(event));
				break;
			case WORKING:
				resourceRequestMap.put(getSafeResource(event), event.getRequest());
				break;
			default:
				break;
		}
	}

	public ResourceRequest getRequestFor(Resource resource) {
		return resourceRequestMap.get(resource);
	}

	private Resource getSafeResource(ManagedResourceRequestEvent event) {
		try {
			return event.getManagedRequest().getResourceFuture().get(10, TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException | InterruptedException | ExecutionException e) {
			return null;
		}
	}
}

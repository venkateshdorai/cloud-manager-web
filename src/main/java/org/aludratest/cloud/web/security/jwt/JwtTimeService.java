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
package org.aludratest.cloud.web.security.jwt;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

/**
 * Service for getting the "current time" for JWT purposes. Can be mocked for
 * unit tests (this is why it exists as a service, although its default
 * implementation is trivial).
 *
 * @author thann
 *
 */
@Service
public class JwtTimeService {

	public LocalDateTime now() {
		return LocalDateTime.now();
	}

}

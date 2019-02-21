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
package org.aludratest.cloud.web.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.aludratest.cloud.web.security.jwt.JwtAuthenticationFilter;
import org.aludratest.cloud.web.security.jwt.JwtTokenValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * Configures Spring Security to use the custom ACM user authenticator and to protect access to all REST API endpoints (
 * <code>/api/**</code>) via HTTP Basic Authentication.
 *
 * @author falbrech
 *
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class ApiAuthenticator extends WebSecurityConfigurerAdapter {

	private static final String LOGIN_ENDPOINT = "/api/login";

	@Autowired
	private CloudManagerAuthenticationProvider authenticationProvider;

	@Autowired
	private JwtTokenValidator jwtTokenValidator;

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http.csrf().ignoringAntMatchers("/api/**").and().antMatcher("/api/**").authorizeRequests()
				.anyRequest()
				.authenticated().and()
				.addFilterBefore(jwtAuthenticationFilter(), BasicAuthenticationFilter.class).httpBasic()
				.authenticationEntryPoint(new Simple401AuthenticationEntryPoint()).and()
				.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and().csrf().disable();
	}

	@Autowired
	public void configureGlobal(AuthenticationManagerBuilder authBuilder) throws Exception {
		authBuilder.authenticationProvider(authenticationProvider);
	}

	@Bean
	public JwtAuthenticationFilter jwtAuthenticationFilter() throws Exception {
		List<String> pathsToSkip = Arrays.asList(LOGIN_ENDPOINT);
		SkipPathRequestMatcher requestMatcher = new SkipPathRequestMatcher(pathsToSkip, "/**");

		JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenValidator, requestMatcher);
		// JwtAuthenticationFailureHandler failureHandler = new
		// JwtAuthenticationFailureHandler(
		// contextPath + LOGIN_ENDPOINT);
		//
		// filter.setAuthenticationFailureHandler(failureHandler);
		filter.setAuthenticationSuccessHandler(jwtAuthenticationSuccessHandler());
		filter.setAuthenticationManager(authenticationManager());
		return filter;
	}

	private AuthenticationSuccessHandler jwtAuthenticationSuccessHandler() {
		return new AuthenticationSuccessHandler() {
			@Override
			public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
				// prevent default behavior
			}

		};
	}

	private static class Simple401AuthenticationEntryPoint implements AuthenticationEntryPoint {
		@Override
		public void commence(HttpServletRequest request, HttpServletResponse response,
				AuthenticationException authException) throws IOException, ServletException {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
		}
	}


}

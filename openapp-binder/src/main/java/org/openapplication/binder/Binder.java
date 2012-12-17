/**
 * Copyright 2012 Erik Isaksson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openapplication.binder;

import java.lang.reflect.Constructor;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Binder {

	private static Logger logger = LoggerFactory.getLogger(Binder.class);

	public static Binder forApplication(String applicationClass,
			String defaultBinderImpl, Object instantiator) {
		String binderImpl = null;
		try {
			binderImpl = System.getProperty("openapp.binder");
			if (binderImpl != null)
				logger.info("Binder implementation is set to value of system property openapp.binder: "
						+ binderImpl);
			else
				logger.info("System property openapp.binder has not been set");
		} catch (SecurityException e) {
			logger.info("Getting system property openapp.binder failed: " + e);
		}
		if (binderImpl == null) {
			try {
				Context ctx = new InitialContext();
				binderImpl = (String) ctx
						.lookup("java:comp/env/openapp/binder");
				logger.info("Binder implementation is set to value of environment variable java:comp/env/openapp/binder: "
						+ binderImpl);
			} catch (NamingException e) {
				logger.info("Lookup of environment variable java:comp/env/openapp/binder failed: "
						+ e);
			}
		}
		if (binderImpl == null && defaultBinderImpl != null) {
			binderImpl = defaultBinderImpl;
			logger.info("Binder implementation is set to default value: "
					+ binderImpl);
		}
		if (binderImpl == null) {
			logger.error("No binder implementation has been set");
			return null;
		}
		try {
			Class<?> binderClass = Class.forName(binderImpl);
			Constructor<?> binderConstructor = binderClass.getConstructor(
					String.class, Object.class);
			return (Binder) binderConstructor.newInstance(applicationClass,
					instantiator);
		} catch (Exception e) {
			throw new Error("Error instantiating binder implementation "
					+ binderImpl, e);
		}
	}

	public abstract Object getApplication();

	public abstract Object getInstantiator();

	public abstract Iterable<String> getProperties(Object instance);

	public abstract String getValue(Object instance, String property);

	public abstract Iterable<String> getValues(Object instance, String property);

	public abstract Object getInstance(Object instance, String property);

	public abstract Iterable<Object> getInstances(Object instance,
			String property);

	public abstract Object getInstance(String property, String value);

	public abstract Iterable<Object> getInstances(String property, String value);

	public abstract Iterable<Object> getInstances(String property);

}

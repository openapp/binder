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
package org.openapplication.binder.graph;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.openapplication.binder.Bindable;
import org.openapplication.binder.Binder;
import org.openapplication.graph.Value;
import org.openapplication.graph.Values;
import org.openapplication.graph.jsonld.JSONLD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.kth.csc.kmr.openapp.graph.store.Graph;

public final class GraphBinder extends Binder {

	private static Logger logger = LoggerFactory.getLogger(GraphBinder.class);

	private final File homeDirectory;

	private final Graph graph = new Graph();

	private final Map<Value, Object> instances = new HashMap<Value, Object>();

	private final Map<Object, Value> entities = new IdentityHashMap<Object, Value>();

	private final Object instantiator;

	private Object applicationInstance;

	private Value applicationEntity;

	public GraphBinder(String applicationClass, Object instantiator) {
		this.instantiator = instantiator;

		String home = null;
		try {
			home = System.getProperty("openapp.home");
			if (home != null)
				logger.info("Binder implementation is set to value of system property openapp.home: "
						+ home);
			else
				logger.info("System property openapp.home has not been set");
		} catch (SecurityException e) {
			logger.info("Getting system property openapp.home failed: " + e);
		}
		if (home == null) {
			try {
				Context ctx = new InitialContext();
				home = (String) ctx.lookup("java:comp/env/openapp/home");
				logger.info("OpenApp home directory is set to value of environment variable java:comp/env/openapp/home: "
						+ home);
			} catch (NamingException e) {
				logger.info("Lookup of environment variable java:comp/env/openapp/home failed: "
						+ e);
			}
		}
		if (home == null) {
			File homeDirectoryCheck = new File("openapp");
			homeDirectory = homeDirectoryCheck.isDirectory() ? homeDirectoryCheck
					: null;
			if (homeDirectory != null)
				logger.info("OpenApp home directory found in working directory: "
						+ homeDirectory.getAbsolutePath());
		} else {
			homeDirectory = new File(home);
			if (homeDirectory.mkdirs())
				logger.info("Created OpenApp home directory: "
						+ this.homeDirectory);
		}
		if (homeDirectory == null) {
			logger.info("OpenApp home directory is not set - will continue without a home directory");
		}

		if (homeDirectory != null) {
			File configuration = new File(homeDirectory, "application.jsonld");
			if (!configuration.exists()) {
				logger.warn("There is no file named application.jsonld in the home directory - a file with default configuration will be created");
				InputStream is = null;
				OutputStream os = null;
				try {
					is = getClass().getClassLoader().getResourceAsStream(
							"application.jsonld");
					os = new FileOutputStream(configuration);
					int read;
					while ((read = is.read()) != -1)
						os.write(read);
				} catch (Exception e) {
					logger.error(
							"Error copying default configuration to file application.jsonld in the home directory",
							e);
				} finally {
					try {
						if (is != null)
							is.close();
						if (os != null)
							os.close();
					} catch (IOException e) {
						logger.error(
								"Error copying default configuration to file application.jsonld in the home directory",
								e);
					}
				}
			}
			InputStream in = null;
			try {
				logger.info("Reading configuration from file application.jsonld in the home directory");
				in = new FileInputStream(configuration);
				JSONLD.parse(in, graph.toReader());
			} catch (Exception e) {
				logger.error(
						"Could not read file application.jsonld in the home directory",
						e);
			} finally {
				if (in != null)
					try {
						in.close();
					} catch (IOException e) {
					}
			}
		} else {
			InputStream in = null;
			try {
				logger.info("Reading default configuration");
				in = getClass().getClassLoader().getResourceAsStream(
						"application.jsonld");
				JSONLD.parse(in, graph.toReader());
			} catch (Exception e) {
				logger.error("Could not read default configuration file", e);
			} finally {
				if (in != null)
					try {
						in.close();
					} catch (IOException e) {
					}
			}
		}

		try {
			for (Enumeration<URL> moduleUrls = getClass().getClassLoader()
					.getResources("META-INF/openapp.jsonld"); moduleUrls
					.hasMoreElements();) {
				URL moduleUrl = moduleUrls.nextElement();
				InputStream in = null;
				try {
					in = moduleUrl.openStream();
					JSONLD.parse(in, graph.toReader());
				} catch (Exception e) {
					logger.error("Could not read classpath resource "
							+ moduleUrl, e);
				} finally {
					if (in != null)
						try {
							in.close();
						} catch (IOException e) {
						}
				}
			}
		} catch (Exception e) {
			logger.error(
					"Error finding resources named META-INF/openapp.jsonld in classpath",
					e);
		}

		for (Value entity : graph.project(
				Values.iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
				Values.iri(applicationClass))) {
			applicationEntity = entity;
			if (homeDirectory != null)
				graph.add(Collections.singleton(Values.triple(
						applicationEntity,
						Values.iri("http://purl.org/openapp/server/homeDirectory"),
						Values.string(homeDirectory.getPath()))));
			getInstance(applicationEntity);
			return;
		}
		throw new Error("Subject definition of application not found");
	}

	@Override
	public Object getApplication() {
		return applicationInstance;
	}

	@Override
	public Object getInstantiator() {
		return instantiator;
	}

	@Override
	public Iterable<String> getProperties(Object instance) {
		List<String> result = new ArrayList<String>();
		for (Value property : graph.properties(getValue(instance)))
			result.add(property.toIri());
		return result;
	}

	@Override
	public String getValue(Object instance, String property) {
		Value value = graph.first(getValue(instance), Values.iri(property));
		if (value == null)
			return null;
		if (value.getString() != null)
			return value.getString();
		return value.toIri();
	}

	@Override
	public Iterable<String> getValues(Object instance, String property) {
		List<String> result = new ArrayList<String>();
		for (Value value : graph.values(getValue(instance),
				Values.iri(property))) {
			if (value.getString() != null)
				result.add(value.getString());
			else
				result.add(value.toIri());
		}
		return result;
	}

	@Override
	public Object getInstance(Object instance, String property) {
		Value entity = graph.first(getValue(instance), Values.iri(property));
		return entity == null ? null : getInstance(entity);
	}

	@Override
	public Iterable<Object> getInstances(Object instance, String property) {
		List<Object> result = new ArrayList<Object>();
		for (Value entity : graph.values(getValue(instance),
				Values.iri(property)))
			result.add(getInstance(entity));
		return result;
	}

	@Override
	public Object getInstance(String property, String value) {
		for (Value entity : graph.project(Values.iri(property),
				Values.iri(value)))
			return getInstance(entity);
		return null;
	}

	@Override
	public Iterable<Object> getInstances(String property, String value) {
		List<Object> result = new ArrayList<Object>();
		for (Value entity : graph.project(Values.iri(property),
				Values.iri(value)))
			result.add(getInstance(entity));
		return result;
	}

	@Override
	public Iterable<Object> getInstances(String property) {
		List<Object> result = new ArrayList<Object>();
		for (Value entity : graph.project(Values.iri(property)))
			result.add(getInstance(entity));
		return result;
	}

	private Value getValue(Object instance) {
		return entities.get(instance);
	}

	private Object getInstance(Value entity) {
		Object instance = instances.get(entity);
		if (instance != null)
			return instance;
		Value implementation = null;
		for (Value impl : graph.values(entity,
				Values.iri("http://purl.org/openapp/server/implementation")))
			if ("http://purl.org/openapp/server/javaFQName".equals(impl
					.getType()))
				implementation = impl;
		try {
			if (implementation == null) {
				instance = new Object();
			} else {
				logger.info("Instantiating " + implementation.getString());
				instance = Class.forName(implementation.getString())
						.newInstance();
			}
			instances.put(entity, instance);
			entities.put(instance, entity);
			if (entity == applicationEntity)
				applicationInstance = instance;
			if (instance instanceof Bindable)
				((Bindable) instance).bind(this);
			return instance;
		} catch (Exception e) {
			throw new Error(
					"Error instantiating " + implementation.getString(), e);
		}
	}

}

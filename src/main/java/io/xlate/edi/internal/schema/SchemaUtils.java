/*******************************************************************************
 * Copyright 2017 xlate.io LLC, http://www.xlate.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package io.xlate.edi.internal.schema;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.Map.Entry;

import io.xlate.edi.schema.EDISchemaException;
import io.xlate.edi.schema.Schema;
import io.xlate.edi.schema.SchemaFactory;
import io.xlate.edi.stream.EDIStreamConstants.Standards;

import java.util.NavigableMap;
import java.util.Properties;
import java.util.TreeMap;

public class SchemaUtils {

    private SchemaUtils() {
    }

    static Properties controlIndex = new Properties();
    static NavigableMap<String, String> controlVersions = new TreeMap<>();
    static NavigableMap<String, Schema> controlSchemas = new TreeMap<>();

    static {
        try {
            Enumeration<URL> resources = getStreams("staedi-control-index.properties");

            while (resources.hasMoreElements()) {
                controlIndex.load(resources.nextElement().openStream());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (Map.Entry<Object, Object> entry : controlIndex.entrySet()) {
            final String standardVersion = entry.getKey().toString();
            final String schemaPath = entry.getValue().toString();

            controlVersions.put(standardVersion, schemaPath);
            controlSchemas.put(standardVersion, null);
        }
    }

    static Enumeration<URL> getStreams(String resource) throws IOException {
        ClassLoader loader = SchemaUtils.class.getClassLoader();
        return loader.getResources(resource);
    }

    static URL getURL(String resource) {
        return SchemaUtils.class.getResource(resource);
    }

    public static Schema getControlSchema(String standard, String[] version) throws EDISchemaException {
        String key;

        if (Standards.EDIFACT.equals(standard)) {
            key = standard + '.' + version[1] + (version.length > 4 ? '.' + version[4] : "");
        } else {
            key = standard + '.' + String.join(".", version);
        }

        Entry<String, Schema> controlEntry = controlSchemas.floorEntry(key);

        if (controlEntry != null && controlEntry.getValue() != null) {
            return controlEntry.getValue();
        }

        Entry<String, String> pathEntry = controlVersions.floorEntry(key);

        if (pathEntry != null) {
            Schema created = getXmlSchema(pathEntry.getValue());
            controlSchemas.put(pathEntry.getKey(), created);
            return created;
        }

        return null;
    }

    private static Schema getXmlSchema(String resource) throws EDISchemaException {
        SchemaFactory schemaFactory = SchemaFactory.newFactory();
        URL location = getURL(resource);
        return schemaFactory.createSchema(location);
    }
}

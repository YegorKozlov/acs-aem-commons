/*
 * #%L
 * ACS AEM Commons Bundle
 * %%
 * Copyright (C) 2016 Adobe
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.adobe.acs.commons.redirects.filter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigurationBuilder {
    private boolean enabled = true;
    private boolean preserveQueryString = true;
    private boolean mapUrls = false;
    private String[] paths = {};
    private String[] headers = {};
    private String bucketName = RedirectFilter.DEFAULT_CONFIG_BUCKET;
    private String configName = RedirectFilter.DEFAULT_CONFIG_NAME;

    public ConfigurationBuilder setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public ConfigurationBuilder preserveQueryString(boolean preserveQueryString) {
        this.preserveQueryString = preserveQueryString;
        return this;
    }

    public ConfigurationBuilder mapUrls(boolean mapUrls) {
        this.mapUrls = mapUrls;
        return this;
    }

    public ConfigurationBuilder paths(String... path) {
        this.paths = path;
        return this;
    }

    public ConfigurationBuilder headers(String... headers) {
        this.headers = headers;
        return this;
    }

    public ConfigurationBuilder bucketName(String bucketName) {
        this.bucketName = bucketName;
        return this;
    }

    public ConfigurationBuilder configName(String configName) {
        this.configName = configName;
        return this;
    }

    RedirectFilter.Configuration build() {
        RedirectFilter.Configuration configuration = mock(RedirectFilter.Configuration.class);
        when(configuration.enabled()).thenReturn(enabled);
        when(configuration.preserveQueryString()).thenReturn(preserveQueryString);
        when(configuration.mapUrls()).thenReturn(mapUrls);
        when(configuration.paths()).thenReturn(paths);
        when(configuration.additionalHeaders()).thenReturn(headers);
        when(configuration.bucketName()).thenReturn(bucketName);
        when(configuration.configName()).thenReturn(configName);
        return configuration;
    }
}

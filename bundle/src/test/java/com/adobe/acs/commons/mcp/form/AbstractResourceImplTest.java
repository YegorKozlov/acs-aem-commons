/*
 * #%L
 * ACS AEM Commons Bundle
 * %%
 * Copyright (C) 2017 Adobe
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
package com.adobe.acs.commons.mcp.form;

import org.apache.sling.api.resource.ResourceMetadata;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AbstractResourceImplTest {

    @Test
    public void testResourceMetadata() {
        // resource metadata is null. resolution path will be initialized from the 'path' argument
        AbstractResourceImpl r1 = new AbstractResourceImpl("/path1", null, null, null);
        assertEquals("/path1", r1.getResourceMetadata().getResolutionPath());

        // resource metadata does not contain resolution path. it will be initialized from the 'path' argument
        AbstractResourceImpl r2 = new AbstractResourceImpl("/path2", null, null, new ResourceMetadata());
        assertEquals("/path2", r2.getResourceMetadata().getResolutionPath());

        // resource metadata contains resolution path
        ResourceMetadata metadata = new ResourceMetadata();
        metadata.put(ResourceMetadata.RESOLUTION_PATH, "/resolutionPath");
        AbstractResourceImpl r3 = new AbstractResourceImpl("/path3", null, null, metadata);
        assertEquals("/resolutionPath", r3.getResourceMetadata().getResolutionPath());
    }
}

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

import com.adobe.acs.commons.redirects.models.RedirectConfiguration;
import com.adobe.acs.commons.redirects.models.RedirectRule;
import com.day.cq.wcm.api.WCMMode;
import org.apache.http.Header;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.builder.ContentBuilder;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RedirectFilterConfigurationTest {

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.RESOURCERESOLVER_MOCK);

    @Mock
    private ResourceResolverFactory resourceResolverFactory;

    @InjectMocks
    private RedirectFilter filter;

    @Before
    public void setUp() throws Exception {
        when(resourceResolverFactory.getServiceResourceResolver(anyMap())).thenReturn(context.resourceResolver());
    }

    /**
     * The default configuration will match requests starting with /content
     * Any other paths like, e.g. /app/*. /lib/* will be ignored
     */
    @Test
    public void testDefaultConfiguration() {
        RedirectFilter.Configuration configuration = new ConfigurationBuilder()
                .paths("/content")
                .build();
        filter.activate(configuration, context.bundleContext());

        MockSlingHttpServletRequest request = context.request();
        context.requestPathInfo().setResourcePath("/content/my-site/page1");
        assertTrue(filter.doesRequestMatch(request));

        context.requestPathInfo().setResourcePath("/lib/page1");
        assertFalse(filter.doesRequestMatch(request));

        context.requestPathInfo().setResourcePath("/page1");
        assertFalse(filter.doesRequestMatch(request));
    }

    /**
     * Setting the 'paths' parameter to '/' will match any request including
     * /content/*, /app/*. /lib/* , etc.
     */
    @Test
    public void testMatchAllPaths() {
        RedirectFilter.Configuration configuration = new ConfigurationBuilder()
                .paths("/")
                .build();
        filter.activate(configuration, context.bundleContext());

        MockSlingHttpServletRequest request = context.request();
        context.requestPathInfo().setResourcePath("/content/my-site/page1");
        assertTrue(filter.doesRequestMatch(request));

        context.requestPathInfo().setResourcePath("/lib/page1");
        assertTrue(filter.doesRequestMatch(request));

        context.requestPathInfo().setResourcePath("/page1");
        assertTrue(filter.doesRequestMatch(request));
    }

    /**
     * If the OSGi 'paths' property is empty it is equivalent to '/', i.e. match all
     */
    @Test
    public void testMatchAllIfConfigEmpty() {
        RedirectFilter.Configuration configuration = new ConfigurationBuilder()
                .paths()
                .build();
        filter.activate(configuration, context.bundleContext());

        MockSlingHttpServletRequest request = context.request();
        context.requestPathInfo().setResourcePath("/content/my-site/page1");
        assertTrue(filter.doesRequestMatch(request));

        context.requestPathInfo().setResourcePath("/lib/page1");
        assertTrue(filter.doesRequestMatch(request));

        context.requestPathInfo().setResourcePath("/page1");
        assertTrue(filter.doesRequestMatch(request));
    }

    /**
     * Redirects are only enabled if wcmmode is 'DISABLED'. In any other modes, e.g. EDIT
     * the filter is disabled
     */
    @Test
    public void testWCMModeDisabled() {
        RedirectFilter.Configuration configuration = new ConfigurationBuilder().build();
        filter.activate(configuration, context.bundleContext());

        MockSlingHttpServletRequest request = context.request();
        assertNull(request.getAttribute(WCMMode.class.getName()));

        context.requestPathInfo().setResourcePath("/content/my-site/page1");
        assertTrue(filter.doesRequestMatch(request));
    }

    /**
     * Redirects are only enabled if wcmmode is 'DISABLED'. In any other modes, e.g. EDIT
     * the filter is disabled
     */
    @Test
    public void testWCMModeEdit() {
        RedirectFilter.Configuration configuration = new ConfigurationBuilder().build();
        filter.activate(configuration, context.bundleContext());

        MockSlingHttpServletRequest request = context.request();
        request.setAttribute(WCMMode.class.getName(), WCMMode.EDIT);

        context.requestPathInfo().setResourcePath("/content/my-site/page1");
        assertFalse(filter.doesRequestMatch(request));
    }

    /**
     * OSGi config can set optional on-delivery headers in the 'name: value' format
     */
    @Test
    public void testOnDeliveryHeaders() {
        RedirectFilter.Configuration configuration = new ConfigurationBuilder()
                .headers("Cache-Control: none", "ACS: one")
                .build();
        filter.activate(configuration, context.bundleContext());

        List<Header> headers = filter.getOnDeliveryHeaders();
        assertEquals(2, headers.size());
        Header header1 = headers.get(0);
        assertEquals("Cache-Control", header1.getName());
        assertEquals("none", header1.getValue());

        Header header2 = headers.get(0);
        assertEquals("Cache-Control", header2.getName());
        assertEquals("none", header2.getValue());
    }

    /**
     * if the definition of a header is invalid (does not match 'name: value' pattern) then it is ignored
     */
    @Test
    public void testInvalidOnDeliveryHeaders() {
        RedirectFilter.Configuration configuration = new ConfigurationBuilder()
                .headers("Cache-Control-Invalid")
                .build();
        filter.activate(configuration, context.bundleContext());

        List<Header> headers = filter.getOnDeliveryHeaders();
        assertEquals(0, headers.size());
    }

    private void withRules(String configPath, RedirectRule... rules) {
        ContentBuilder cb = context.create();
        int c = 0;
        for (RedirectRule rule : rules) {
            cb.resource(configPath + "/rule-" + c,
                    "sling:resourceType", "acs-commons/components/utilities/manage-redirects/redirect-row",
                    "source", rule.getSource(),
                    "target", rule.getTarget(), "statusCode", rule.getStatusCode(),
                    "untilDate", rule.getUntilDate() == null ? null : GregorianCalendar.from(rule.getUntilDate()));
            c++;
        }
    }

    @Test
    public void testLoadRules() {
        RedirectFilter.Configuration configuration = new ConfigurationBuilder().build();
        filter.activate(configuration, context.bundleContext());

        String configRoot = "/conf/global/" + configuration.bucketName() + "/" + configuration.configName();
        withRules(
                configRoot,
                new RedirectRule("/content/we-retail/en/one", "/content/we-retail/en/two", 302, null, null),
                new RedirectRule("/content/we-retail/en/three", "/content/we-retail/en/four", 301, null, null),
                new RedirectRule("/content/we-retail/en/events/*", "/content/we-retail/en/four", 301, null, null)
        );
        RedirectConfiguration cfg = filter.loadRules(configRoot);
        Collection<RedirectRule> pathRules = cfg.getPathRules().values();
        Collection<RedirectRule> patternRules = cfg.getPatternRules().values();

        assertEquals(2, pathRules.size());
        assertEquals(1, patternRules.size());

        Iterator<RedirectRule> it = pathRules.iterator();
        RedirectRule rule1 = it.next();
        assertEquals("/content/we-retail/en/one", rule1.getSource());
        assertEquals("/content/we-retail/en/two", rule1.getTarget());
        assertEquals(302, rule1.getStatusCode());

        RedirectRule rule2 = it.next();
        assertEquals("/content/we-retail/en/three", rule2.getSource());
        assertEquals("/content/we-retail/en/four", rule2.getTarget());
        assertEquals(301, rule2.getStatusCode());

        RedirectRule rule3 = patternRules.iterator().next();
        assertEquals("/content/we-retail/en/events/*", rule3.getSource());
        assertEquals("/content/we-retail/en/four", rule3.getTarget());
        assertEquals(301, rule3.getStatusCode());
        assertNotNull(rule3.getRegex());

    }

    @Test
    public void testNotEnabledDeactivate() throws Exception {
        RedirectFilter.Configuration configuration = mock(RedirectFilter.Configuration.class);
        when(configuration.enabled()).thenReturn(false);

        RedirectFilter redirectFilter = spy(new RedirectFilter());
        redirectFilter.activate(configuration, context.bundleContext());

        // #2673 : ensure no NPE in deactivate() when filter is disabled
        redirectFilter.deactivate();
    }


}
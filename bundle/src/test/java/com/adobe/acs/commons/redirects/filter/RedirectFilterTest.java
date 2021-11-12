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

import com.adobe.acs.commons.redirects.LocationHeaderAdjuster;
import com.adobe.acs.commons.redirects.models.RedirectConfiguration;
import com.adobe.acs.commons.redirects.models.RedirectRule;
import com.day.cq.wcm.api.WCMMode;
import com.google.common.cache.Cache;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.wrappers.SlingHttpServletRequestWrapper;
import org.apache.sling.caconfig.resource.ConfigurationResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.builder.ContentBuilder;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.powermock.reflect.Whitebox;

import javax.management.openmbean.TabularData;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.GregorianCalendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RedirectFilterTest {

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.RESOURCERESOLVER_MOCK);

    @Mock
    private ConfigurationResourceResolver configResolver;

    @Mock
    private FilterChain filterChain;

    @Mock
    private ResourceResolverFactory resourceResolverFactory;

    @InjectMocks
    @Spy
    private RedirectFilter filter;

    private final String configPath = "/conf/global/settings/redirects";

    private Answer<String> mapUrlMock; // mock impl of ResourceResolver#map

    @Before
    public void setUp() throws Exception {
        when(resourceResolverFactory.getServiceResourceResolver(anyMap())).thenReturn(context.resourceResolver());

        mapUrlMock = invocation -> {
            String path = invocation.getArgument(0, String.class);
            // /content/we-retail/en/page.html --> https://www.we-retail.com/en/page.html
            if (path.startsWith("/content/we-retail/")) {
                return path
                        .replace("/content/we-retail/", "https://www.we-retail.com/");
            } else {
                return path;
            }
        };

        RedirectFilter.Configuration configuration = new ConfigurationBuilder().build();
        filter.activate(configuration, context.bundleContext());
    }

    private void withRules(RedirectRule... rules) {
        withRules(configPath, rules);

    }

    private void withRules(String configPath, RedirectRule... rules) {
        ContentBuilder cb = context.create();
        Resource configResource = cb.resource(configPath);
        int c = 0;
        for (RedirectRule rule : rules) {
            cb.resource(configPath + "/rule-" + c,
                    "sling:resourceType", "acs-commons/components/utilities/manage-redirects/redirect-row",
                    "source", rule.getSource(),
                    "target", rule.getTarget(), "statusCode", rule.getStatusCode(),
                    "untilDate", rule.getUntilDate() == null ? null : GregorianCalendar.from(rule.getUntilDate()));
            c++;
        }
        doAnswer(invocation -> configResource).when(configResolver).getResource(any(Resource.class), any(String.class), any(String.class));
    }

    private MockSlingHttpServletResponse navigate(String requestURI) throws IOException, ServletException {
        MockSlingHttpServletRequest request = context.request();
        String resourcePath, extension;
        int idx = requestURI.lastIndexOf('.');
        if (idx > 0) {
            resourcePath = requestURI.substring(0, idx);
            extension = requestURI.substring(idx + 1);
            context.requestPathInfo().setExtension(extension);
        } else {
            resourcePath = requestURI;
        }
        context.requestPathInfo().setResourcePath(resourcePath);
        request.setResource(context.create().resource(resourcePath));

        MockSlingHttpServletResponse response = context.response();
        filter.doFilter(request, response, filterChain);

        return response;
    }

    @Test
    public void testNavigate302() throws Exception {
        withRules(
                new RedirectRule("/content/geometrixx/en/one", "/content/geometrixx/en/two",
                        302, null, null));
        MockSlingHttpServletResponse response = navigate("/content/geometrixx/en/one.html");

        assertEquals(302, response.getStatus());
        assertEquals("/content/geometrixx/en/two.html", response.getHeader("Location"));
        verify(filterChain, never())
                .doFilter(any(SlingHttpServletRequest.class), any(SlingHttpServletResponse.class));
    }


    @Test
    public void testNavigateToExternalSite() throws Exception {
        withRules(
                new RedirectRule("/content/geometrixx/en/one", "https://www.geometrixx.com",
                        302, null, null));
        MockSlingHttpServletResponse response = navigate("/content/geometrixx/en/one.html");

        assertEquals(302, response.getStatus());
        assertEquals("https://www.geometrixx.com", response.getHeader("Location"));
        verify(filterChain, never())
                .doFilter(any(SlingHttpServletRequest.class), any(SlingHttpServletResponse.class));
    }

    @Test
    public void testNavigate301() throws Exception {
        withRules(
                new RedirectRule("/content/we-retail/en/one", "/content/we-retail/en/two",
                        301, null, null));
        MockSlingHttpServletResponse response = navigate("/content/we-retail/en/one.html");

        assertEquals(301, response.getStatus());
        assertEquals("/content/we-retail/en/two.html", response.getHeader("Location"));
        verify(filterChain, never())
                .doFilter(any(SlingHttpServletRequest.class), any(SlingHttpServletResponse.class));
    }

    @Test
    public void testNavigateNoRewrite() throws Exception {
        withRules(
                new RedirectRule("/content/we-retail/en/one", "/content/we-retail/en/two",
                        302, null, null));

        MockSlingHttpServletResponse response = navigate("/content/we-retail/en/one.html");
        verify(filter, never())
                .mapUrl(anyString(), any(SlingHttpServletRequest.class));

        assertEquals(302, response.getStatus());
        assertEquals("/content/we-retail/en/two.html", response.getHeader("Location"));
        verify(filterChain, never())
                .doFilter(any(SlingHttpServletRequest.class), any(SlingHttpServletResponse.class));
    }

    @Test
    public void testPreserveQueryString() throws Exception {

        withRules(
                new RedirectRule("/content/geometrixx/en/one", "/content/geometrixx/en/two",
                        302, null, null));

        context.request().setQueryString("a=1&b=2");
        MockSlingHttpServletResponse response = navigate("/content/geometrixx/en/one.html");

        assertEquals(302, response.getStatus());
        assertEquals("/content/geometrixx/en/two.html?a=1&b=2", response.getHeader("Location"));
        verify(filterChain, never())
                .doFilter(any(SlingHttpServletRequest.class), any(SlingHttpServletResponse.class));
    }

    @Test
    public void testMatchSingleAsset() throws Exception {
        withRules(
                new RedirectRule("/content/dam/we-retail/en/events/test.pdf", "/content/dam/geometrixx/en/target/test.pdf",
                        302, null, null));
        MockSlingHttpServletResponse response = navigate("/content/dam/we-retail/en/events/test.pdf");

        verify(filterChain, never())
                .doFilter(any(SlingHttpServletRequest.class), any(SlingHttpServletResponse.class));

        assertEquals("/content/dam/geometrixx/en/target/test.pdf", response.getHeader("Location"));
    }

    @Test
    public void testMatchWithHtmlExtension() throws Exception {
        withRules(
                new RedirectRule("/content/we-retail/en/events/test.html", "/content/we-retail/en.html",
                        302, null, null));
        MockSlingHttpServletResponse response = navigate("/content/we-retail/en/events/test.html");

        verify(filterChain, never())
                .doFilter(any(SlingHttpServletRequest.class), any(SlingHttpServletResponse.class));

        assertEquals("/content/we-retail/en.html", response.getHeader("Location"));
    }

    @Test
    public void testMatchRegexAsset() throws Exception {
        withRules(
                new RedirectRule("/content/dam/we-retail/en/events/(.*?).pdf", "/content/dam/geometrixx/en/target/welcome.pdf",
                        302, null, null));

        assertEquals("/content/dam/geometrixx/en/target/welcome.pdf",
                navigate("/content/dam/we-retail/en/events/one.pdf").getHeader("Location"));
    }

    @Test
    public void testNotMatchRegexAsset() throws Exception {
        withRules(
                new RedirectRule("/content/dam/we-retail/en/events/(.*?).pdf", "/content/dam/geometrixx/en/target/welcome.pdf",
                        302, null, null));

        assertNull("Unexpected redirect", navigate("/content/dam/we-retail/en/events/one.txt").getHeader("Location"));
    }

    @Test
    public void testLeadingSpaces() throws Exception {
        withRules(
                new RedirectRule(" /content/we-retail/en/one", " /content/we-retail/en/two",
                        302, null, null));
        MockSlingHttpServletResponse response = navigate("/content/we-retail/en/one");

        verify(filterChain, never())
                .doFilter(any(SlingHttpServletRequest.class), any(SlingHttpServletResponse.class));

        assertEquals("/content/we-retail/en/two", response.getHeader("Location"));
    }

    @Test
    public void testTrailingSpaces() throws Exception {
        withRules(
                new RedirectRule(" /content/we-retail/en/one ", " /content/we-retail/en/two ",
                        302, null, null));
        MockSlingHttpServletResponse response = navigate("/content/we-retail/en/one");

        verify(filterChain, never())
                .doFilter(any(SlingHttpServletRequest.class), any(SlingHttpServletResponse.class));

        assertEquals("/content/we-retail/en/two", response.getHeader("Location"));
    }

    @Test
    public void testUnsupportedExtension() throws Exception {
        withRules(
                new RedirectRule(" /content/we-retail/en/one ", " /content/we-retail/en/two ",
                        302, null, null));
        when(filter.getExtensions()).thenReturn(Collections.singletonList("html"));
        MockSlingHttpServletResponse response = navigate("/content/we-retail/en/one.json");

        assertNull("Unexpected redirect", response.getHeader("Location"));
        verify(filterChain, atLeastOnce())
                .doFilter(any(SlingHttpServletRequest.class), any(SlingHttpServletResponse.class));
    }

    @Test
    public void testUnsupportedContentRoot() throws Exception {
        withRules(
                new RedirectRule(" /content/we-retail/en/one ", " /content/we-retail/en/two ",
                        302, null, null));
        MockSlingHttpServletResponse response = navigate("/etc/tags/omg");

        assertNull("Unexpected redirect", response.getHeader("Location"));
        verify(filterChain, atLeastOnce())
                .doFilter(any(SlingHttpServletRequest.class), any(SlingHttpServletResponse.class));
    }

    @Test
    public void testUnsupportedMethod() throws Exception {
        withRules(
                new RedirectRule(" /content/we-retail/en/one ", " /content/we-retail/en/two ",
                        302, null, null));
        context.request().setMethod("POST");
        MockSlingHttpServletResponse response = navigate("/content/we-retail/en/one.html");

        assertNull("Unexpected redirect", response.getHeader("Location"));
        verify(filterChain, atLeastOnce())
                .doFilter(any(SlingHttpServletRequest.class), any(SlingHttpServletResponse.class));
    }

    @Test
    public void testAuthorEditWCMMode() throws Exception {
        withRules(
                new RedirectRule(" /content/we-retail/en/one ", " /content/we-retail/en/two ",
                        302, null, null));
        context.request().setAttribute(WCMMode.class.getName(), WCMMode.EDIT);
        MockSlingHttpServletResponse response = navigate("/content/we-retail/en/one.html");

        assertNull("Unexpected redirect", response.getHeader("Location"));
        verify(filterChain, atLeastOnce())
                .doFilter(any(SlingHttpServletRequest.class), any(SlingHttpServletResponse.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInvalidateOnChange() {
        Cache<String, RedirectConfiguration> rulesCache = mock(Cache.class);
        Whitebox.setInternalState(filter, "rulesCache", rulesCache);

        withRules("/conf/global/settings/redirects",
                new RedirectRule("/content/we-retail/en/one", "/content/we-retail/en/two",
                        301, null, null));

        filter.invalidate("/conf/global/settings/redirects/redirect-1");
        verify(rulesCache, times(1)).invalidate(eq("/conf/global/settings/redirects"));

        withRules("/conf/my-site/en/settings/redirects",
                new RedirectRule("/content/my-site/en/one", "/contentmy-site/en/two",
                        301, null, null));
        filter.invalidate("/conf/my-site/en/settings/redirects/redirect-1");
        verify(rulesCache, times(1)).invalidate(eq("/conf/my-site/en/settings/redirects"));
    }

    @Test
    public void testNoopRewrite() throws Exception {
        withRules(new RedirectRule("(.*)", "$1", 302, null, null));
        assertEquals("/content/geometrixx/about/contact-us", navigate("/content/geometrixx/about/contact-us").getHeader("Location"));
    }

    @Test
    public void testPathRewrite1() throws Exception {
        withRules(new RedirectRule("/content/geometrixx/(.+)/contact-us", "/content/geometrixx/$1/about-us", 302, null, null));
        assertEquals("/content/geometrixx/about/about-us",
                navigate("/content/geometrixx/about/contact-us").getHeader("Location"));
    }

    @Test
    public void testPathRewrite2() throws Exception {
        withRules(new RedirectRule("/content/geometrixx/(en)/(.+)/contact-us", "/content/geometrixx/us/$2/contact-us", 302, null, null));
        assertEquals("/content/geometrixx/us/1/contact-us",
                navigate("/content/geometrixx/en/1/contact-us").getHeader("Location"));
        assertEquals("/content/geometrixx/us/1/2/contact-us",
                navigate("/content/geometrixx/en/1/2/contact-us").getHeader("Location"));
    }

    @Test
    public void testPathRewrite3() throws Exception {
        withRules(new RedirectRule("/content/geometrixx/(en)/(.*?/?)contact-us", "/content/geometrixx/us/$2contact-us", 302, null, null));
        assertEquals("/content/geometrixx/us/contact-us", navigate("/content/geometrixx/en/contact-us").getHeader("Location"));
        assertEquals("/content/geometrixx/us/1/contact-us", navigate("/content/geometrixx/en/1/contact-us").getHeader("Location"));
        assertEquals("/content/geometrixx/us/1/2/contact-us", navigate("/content/geometrixx/en/1/2/contact-us").getHeader("Location"));
    }

    @Test
    public void testPathRewrite4() throws Exception {
        withRules(new RedirectRule("/content/geometrixx/(en)/(.+)/contact-us", "/content/geometrixx/us/$2/contact-us#section", 302, null, null));
        assertEquals("/content/geometrixx/us/1/contact-us#section", navigate("/content/geometrixx/en/1/contact-us").getHeader("Location"));
    }

    @Test
    public void testPathRewrite5() throws Exception {
        withRules(new RedirectRule("/content/geometrixx/en/research/(.*)", "/content/geometrixx/en/search?keywords=talent-management", 302, null, null));
        assertEquals("/content/geometrixx/en/search?keywords=talent-management", navigate("/content/geometrixx/en/research/doc").getHeader("Location"));
    }

    @Test
    public void testPathRewrite6() throws Exception {
        withRules(new RedirectRule("/content/geometrixx/(.+)/contact-us#anchor", "/content/geometrixx/$1/contact-us#updated", 302, null, null));
        assertEquals("/content/geometrixx/en/about/contact-us#updated", navigate("/content/geometrixx/en/about/contact-us#anchor").getHeader("Location"));
    }

    @Test
    public void testInvalidRules() throws Exception {
        withRules(
                new RedirectRule("/content/we-retail/(.+", "/content/we-retail/$a", 302, null, null),
                new RedirectRule("/content/we-retail-events/(.+", "/content/we-retail/$", 302, null, null));
        assertNull("Unexpected redirect", navigate("/content/we-retail/en/about/contact-us").getHeader("Location"));
        assertNull("Unexpected redirect", navigate("/content/we-retail-events/en/about/contact-us").getHeader("Location"));
    }

    @Test
    public void testUntilDateRedirectExpired() throws Exception {
        ZonedDateTime dateInPast = ZonedDateTime.now().minusDays(1);
        withRules(
                new RedirectRule("/content/we-retail/en/contact-us", "/content/we-retail/en/contact-them",
                        302, GregorianCalendar.from(dateInPast), null));
        assertNull("Unexpected redirect", navigate("/content/we-retail/en/contact-us").getHeader("Location"));
    }

    @Test
    public void testUntilDateInFuture() throws Exception {
        ZonedDateTime dateInFuture = ZonedDateTime.now().plusDays(1);
        withRules(
                new RedirectRule("/content/geometrixx/en/contact-us", "/content/geometrixx/en/contact-them",
                        302, GregorianCalendar.from(dateInFuture), null));
        assertEquals("/content/geometrixx/en/contact-them", navigate("/content/geometrixx/en/contact-us").getHeader("Location"));
    }

    /**
     * Validate that the Location header is passed through ResourceResolver#map before  delivery
     */
    @Test
    public void testNavigateToMappedUrl() throws Exception {
        doReturn(true).when(filter).mapUrls(); // activate ResourceResolver#map'ing
        doAnswer(mapUrlMock).when(filter).mapUrl(anyString(), any(SlingHttpServletRequest.class)); // mock impl of ResourceResolver#map

        withRules(
                new RedirectRule("/content/we-retail/en/one", "/content/we-retail/en/two",
                        302, null, null));
        MockSlingHttpServletResponse response = navigate("/content/we-retail/en/one.html");
        verify(filter, atLeastOnce())
                .mapUrl(anyString(), any(SlingHttpServletRequest.class));

        assertEquals(302, response.getStatus());
        assertEquals("https://www.we-retail.com/en/two.html", response.getHeader("Location"));
        verify(filterChain, never())
                .doFilter(any(SlingHttpServletRequest.class), any(SlingHttpServletResponse.class));
    }

    /**
     * If ResourceResolver#map'ing is enabled then the RedirectFilter will first try to match the resolved
     * path (/content/we-retail/en/one) and then the mapped path (/en/one). This makes it possible to define
     * redirects in either form:
     *
     * <code>
     * /content/we-retail/en/one ==> targetUrl
     * /en/one ==> targetUrl
     * </code>
     */
    @Test
    public void testMatchWithRewrite() throws Exception {
        doReturn(true).when(filter).mapUrls(); // activate ResourceResolver#map'ing
        doAnswer(mapUrlMock).when(filter).mapUrl(anyString(), any(SlingHttpServletRequest.class)); // mock impl of ResourceResolver#map

        withRules(
                new RedirectRule("/en/one", "/en/two",
                        302, null, null));
        MockSlingHttpServletResponse response = navigate("/content/we-retail/en/one.html");

        assertEquals(302, response.getStatus());
        assertEquals("/en/two.html", response.getHeader("Location"));
        verify(filterChain, never())
                .doFilter(any(SlingHttpServletRequest.class), any(SlingHttpServletResponse.class));
    }


    @Test
    public void testLocationAdjuster() throws Exception {
        LocationHeaderAdjuster urlAdjuster = (request, location) -> location.replace(".html", ".adjusted.html");
        Whitebox.setInternalState(filter, "urlAdjuster", urlAdjuster);
        withRules(
                new RedirectRule("/content/geometrixx/en/one", "/content/geometrixx/en/two",
                        302, null, null));
        MockSlingHttpServletResponse response = navigate("/content/geometrixx/en/one.html");

        assertEquals(302, response.getStatus());
        assertEquals("/content/geometrixx/en/two.adjusted.html", response.getHeader("Location"));
    }

    @Test
    public void testJxmTabularData() throws Exception {
        withRules(
                new RedirectRule("/content/geometrixx/en/one", "/content/geometrixx/en/two",
                        302, null, null),
                new RedirectRule("/content/geometrixx/en/contact-us", "/content/geometrixx/en/contact-them",
                        302, null, null));

        TabularData data = filter.getRedirectRules(configPath);
        assertEquals(0, data.size());

        assertEquals("/content/geometrixx/en/two", navigate("/content/geometrixx/en/one").getHeader("Location"));

        data = filter.getRedirectRules(configPath);
        assertEquals(2, data.size());
    }

    @Test
    public void testNotEnabledOnChange() throws Exception {

        RedirectFilter.Configuration configuration = mock(RedirectFilter.Configuration.class);
        when(configuration.enabled()).thenReturn(false);

        RedirectFilter redirectFilter = spy(new RedirectFilter());
        redirectFilter.activate(configuration, context.bundleContext());

        ResourceChange event = new ResourceChange(ResourceChange.ChangeType.CHANGED,
                "/conf/global/setting/redirects/rule", false, null, null, null);

        // #2673 : ensure no NPE in onChange() when filter is disabled
        redirectFilter.onChange(Collections.singletonList(event));
    }

    /**
     * Match a vanityPath: /vanityPath ==> /content/geometrixx/en/two
     */
    @Test
    public void testMatchVanityPath() throws Exception {

        withRules(
                new RedirectRule("/vanityPath", "/content/geometrixx/en/two",
                        302, null, null));

        MockSlingHttpServletRequest request = context.request();
        String resourcePath = "/content/my-site/page";
        context.requestPathInfo().setResourcePath(resourcePath);
        context.requestPathInfo().setExtension("html");
        request.setResource(context.create().resource(resourcePath));

        // in case of a vanity path request.getRequestURI() returns the vanityPath
        // while request.getRequestPathInfo().getResourcePath() returns the resolved path
        SlingHttpServletRequest requestWrapper = new SlingHttpServletRequestWrapper(request) {
            @Override
            public String getRequestURI() {
                return "/vanityPath";
            }
        };


        MockSlingHttpServletResponse response = context.response();
        filter.doFilter(requestWrapper, response, filterChain);

        assertEquals(302, response.getStatus());
        assertEquals("/content/geometrixx/en/two.html", response.getHeader("Location"));
        verify(filterChain, never())
                .doFilter(any(SlingHttpServletRequest.class), any(SlingHttpServletResponse.class));
    }

}
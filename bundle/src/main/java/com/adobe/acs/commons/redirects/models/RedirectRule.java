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
package com.adobe.acs.commons.redirects.models;

import com.adobe.granite.security.user.util.AuthorizableUtil;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Optional;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Model(adaptables = Resource.class)
public class RedirectRule {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static final String SOURCE_PROPERTY_NAME = "source";
    public static final String TARGET_PROPERTY_NAME = "target";
    public static final String STATUS_CODE_PROPERTY_NAME = "statusCode";
    public static final String UNTIL_DATE_PROPERTY_NAME = "untilDate";
    public static final String NOTE_PROPERTY_NAME = "note";
    public static final String CONTEXT_PREFIX_IGNORED = "contextPrefixIgnored";
    public static final String CREATED_BY = "jcr:createdBy";

    @ValueMapValue
    private String source;

    @ValueMapValue
    private String target;

    @ValueMapValue
    private int statusCode;

    @ValueMapValue
    @Optional
    private String note;

    @ValueMapValue(name = CREATED_BY)
    @Optional
    private String createdBy;

    @ValueMapValue
    @Optional
    private boolean contextPrefixIgnored;

    @ValueMapValue
    @Optional
    private Calendar untilDate;

    @Self
    private Resource resource;

    private ZonedDateTime zonedUntilDate;

    private Pattern ptrn;

    private SubstitutionElement[] substitutions;

    public RedirectRule() {
    }

    public RedirectRule(String source, String target, int statusCode, Calendar calendar, String note) {
        this(source, target, statusCode, calendar, note, false);
    }

    public RedirectRule(String source, String target, int statusCode, Calendar calendar, String note, boolean contextPrefixIgnored) {
        this(source, target, statusCode, calendar, note, contextPrefixIgnored, null);
    }

    public RedirectRule(String source, String target, int statusCode, Calendar calendar, String note, boolean contextPrefixIgnored, String createdBy) {
        this.source = source.trim();
        this.target = target.trim();
        this.statusCode = statusCode;
        this.note = note;
        this.contextPrefixIgnored = contextPrefixIgnored;
        this.createdBy = createdBy;

        String regex = this.source;
        if (regex.endsWith("*")) {
            regex = regex.replaceAll("\\*$", "(.*)");
        }
        ptrn = toRegex(regex);
        substitutions = SubstitutionElement.parse(this.target);
        untilDate = calendar;
        if (untilDate != null) {
            zonedUntilDate = ZonedDateTime.ofInstant( untilDate.toInstant(), untilDate.getTimeZone().toZoneId());
        }
    }

    @PostConstruct
    protected void init() {
        createdBy = AuthorizableUtil.getFormattedName(resource.getResourceResolver(), createdBy);
        if (untilDate != null) {
            zonedUntilDate = ZonedDateTime.ofInstant( untilDate.toInstant(), untilDate.getTimeZone().toZoneId());
        }
    }

    public static RedirectRule from(Resource resource) {
        ValueMap valueMap = resource.getValueMap();
        String source = valueMap.get(SOURCE_PROPERTY_NAME, "");
        String target = valueMap.get(TARGET_PROPERTY_NAME, "");
        String note = valueMap.get(NOTE_PROPERTY_NAME, "");
        String createdBy = AuthorizableUtil.getFormattedName(resource.getResourceResolver(), valueMap.get(CREATED_BY, ""));
        int statusCode = valueMap.get(STATUS_CODE_PROPERTY_NAME, 0);
        boolean contextPrefixIgnored = valueMap.get(CONTEXT_PREFIX_IGNORED, false);
        Calendar calendar = null;
        if(valueMap.containsKey(UNTIL_DATE_PROPERTY_NAME)){
            Object o = valueMap.get(UNTIL_DATE_PROPERTY_NAME);
            if(o instanceof Calendar) {
                calendar = (Calendar)o;
            }
        }
        return new RedirectRule(source, target, statusCode, calendar, note, contextPrefixIgnored, createdBy);
    }

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public String getNote() {
        return note;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public boolean getContextPrefixIgnored() {
        return contextPrefixIgnored;
    }

    public Pattern getRegex() {
        return ptrn;
    }

    public ZonedDateTime getUntilDate() {
        return zonedUntilDate;
    }

    @Override
    public String toString() {
        return String.format("RedirectRule{source='%s', target='%s', statusCode=%s, untilDate=%s, note=%s, contextPrefixIgnored=%s}",
                source, target, statusCode, zonedUntilDate, note, contextPrefixIgnored);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RedirectRule that = (RedirectRule) o;

        return Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return source != null ? source.hashCode() : 0;
    }

    public String evaluate(Matcher matcher) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < substitutions.length; i++) {
            buf.append(substitutions[i].evaluate(matcher));
        }
        return buf.toString();
    }

    static Pattern toRegex(String src) {
        Pattern ptrn;
        try {
            ptrn = Pattern.compile(src);
            int groupCount = ptrn.matcher("").groupCount();
            if (groupCount == 0) {
                ptrn = null;
            }
        } catch (PatternSyntaxException e) {
            log.info("invalid regex: {}", src);
            ptrn = null;
        }
        return ptrn;
    }

}

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
import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.InjectionStrategy;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.Objects;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class RedirectRule {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static final String SOURCE_PROPERTY_NAME = "source";
    public static final String TARGET_PROPERTY_NAME = "target";
    public static final String STATUS_CODE_PROPERTY_NAME = "statusCode";
    public static final String UNTIL_DATE_PROPERTY_NAME = "untilDate";
    public static final String NOTE_PROPERTY_NAME = "note";
    public static final String CONTEXT_PREFIX_IGNORED = "contextPrefixIgnored";
    public static final String CREATED_BY = "jcr:createdBy";
    public static final String MODIFIED_BY = "jcr:lastModifiedBy";
    public static final String TAGS = "cq:tags";

    @ValueMapValue(injectionStrategy = InjectionStrategy.REQUIRED)
    private String source;

    @ValueMapValue(injectionStrategy = InjectionStrategy.REQUIRED)
    private String target;

    @ValueMapValue(injectionStrategy = InjectionStrategy.REQUIRED)
    private int statusCode;

    @ValueMapValue
    private String note;

    @ValueMapValue(name = CREATED_BY)
    private String createdBy;

    @ValueMapValue(name = MODIFIED_BY)
    private String modifiedBy;

    @ValueMapValue
    private boolean contextPrefixIgnored;

    @ValueMapValue(name = TAGS)
    private String[] tagIds;

    @Self
    private Resource resource;

    private ZonedDateTime untilDate;

    private Pattern ptrn;

    private SubstitutionElement[] substitutions;

    @PostConstruct
    protected void init() {
        source = source.trim();
        target = target.trim();
        createdBy = AuthorizableUtil.getFormattedName(resource.getResourceResolver(), createdBy);
        modifiedBy = AuthorizableUtil.getFormattedName(resource.getResourceResolver(), modifiedBy);
        if (resource.getValueMap().containsKey(UNTIL_DATE_PROPERTY_NAME)) {
            Object o = resource.getValueMap().get(UNTIL_DATE_PROPERTY_NAME);
            if (o instanceof Calendar) {
                Calendar calendar = (Calendar) o;
                untilDate = ZonedDateTime.ofInstant(calendar.toInstant(), calendar.getTimeZone().toZoneId());
            }
        }
        initRegexSubstitutions();
    }

    private void initRegexSubstitutions() {
        String regex = this.source;
        if (regex.endsWith("*")) {
            regex = regex.replaceAll("\\*$", "(.*)");
        }
        ptrn = toRegex(regex);
        substitutions = SubstitutionElement.parse(this.target);
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

    public String getModifiedBy() {
        return modifiedBy;
    }

    public boolean getContextPrefixIgnored() {
        return contextPrefixIgnored;
    }

    public Pattern getRegex() {
        return ptrn;
    }

    public ZonedDateTime getUntilDate() {
        return untilDate;
    }

    /**
     * This is ugly, but needed to format untilDate in HTL in the redirect-row component.
     * <p>
     * AEM as a Cloud Service supports formatting java.time.Instant (SLING-10651), but
     * classic AEMs 6.4 and 6.5 only support formatting java.util.Date and java.util.Calendar
     *
     * @return java.util.Calendar representation of untilDate
     */
    public Calendar getUntilDateCalendar() {
        return untilDate == null ? null : GregorianCalendar.from(untilDate);
    }

    public String[] getTagIds() {
        return tagIds;
    }

    /**
     * used in the redirect-row component to print tags in HTL
     */
    public List<Tag> getTags() {
        TagManager tagMgr = resource.getResourceResolver().adaptTo(TagManager.class);
        if(tagIds == null || tagMgr == null){
            return Collections.emptyList();
        }
        List<Tag> tags = new ArrayList<>();
        for(String tagId : tagIds){
            Tag tag = tagMgr.resolve(tagId);
            if(tag != null) {
                tags.add(tag);
            }
        }
        return tags;
    }

    @Override
    public String toString() {
        return String.format("RedirectRule{source='%s', target='%s', statusCode=%s, untilDate=%s, note=%s, " +
                        "contextPrefixIgnored=%s, tags=%s, createdBy=%s, modifiedBy=%s}",
                source, target, statusCode, untilDate, note, contextPrefixIgnored,
                Arrays.toString(tagIds), createdBy, modifiedBy);
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

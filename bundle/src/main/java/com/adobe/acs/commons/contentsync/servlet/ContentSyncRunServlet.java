package com.adobe.acs.commons.contentsync.servlet;

import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.servlets.post.JSONResponse;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import static com.adobe.acs.commons.contentsync.ContentSyncJobConsumer.JOB_TOPIC;


@Component(service = Servlet.class, immediate = true, property = {
        "sling.servlet.extensions=json",
        "sling.servlet.methods=POST",
        "sling.servlet.selectors=run",
        "sling.servlet.resourceTypes=acs-commons/components/utilities/contentsync",
})
@Designate(ocd = ContentSyncRunServlet.Config.class)
public class ContentSyncRunServlet extends SlingAllMethodsServlet {

    @ObjectClassDefinition()
    @interface Config {
        @AttributeDefinition(name = "Allowed Groups", type = AttributeType.STRING)
        String[] allowedGroups() default {};
    }

    public static final String JOB_ID = "jobId";
    public static final String JOB_STATUS = "status";

    @Reference
    private JobManager jobManager;
    private Config config;

    @Activate
    void activate(Config config) {
        this.config = config;
    }

    @Override
    protected void doPost(SlingHttpServletRequest slingRequest, SlingHttpServletResponse slingResponse) throws IOException {
        try {
            checkAccess(slingRequest);

            Job job = submitJob(slingRequest);

            JsonObjectBuilder result = Json.createObjectBuilder();
            result.add(JOB_ID, job.getId());
            result.add(JOB_STATUS, job.getJobState().toString());

            slingResponse.setContentType("application/json");
            try (JsonWriter out = Json.createWriter(slingResponse.getWriter())) {
                out.writeObject(result.build());
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));

            JSONResponse jsonResponse = new JSONResponse();
            jsonResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to submit content-sync job: " + e.getMessage());
            jsonResponse.send(slingResponse, true);
            jsonResponse.setProperty("error", sw.toString());
        }
    }

    /**
     * create a job to build catalog of resources.
     * All request parameters are passed to the job properties.
     */
    Job submitJob(SlingHttpServletRequest slingRequest) {
        Map<String, Object> jobProps = new HashMap<>();
        String catalogServlet = slingRequest.getResource().getPath() + ".catalog.json";
        jobProps.put("catalogServlet", catalogServlet);
        jobProps.put("cq:startedBy", slingRequest.getResourceResolver().getUserID());
        slingRequest.getParameterMap().forEach((key, value) -> jobProps.put(key, value[0]));
        return jobManager.addJob(JOB_TOPIC, jobProps);
    }

    void checkAccess(SlingHttpServletRequest request) throws IllegalAccessException, RepositoryException {
        Set<String> groupIds = new HashSet<>();
        if (config.allowedGroups() != null) groupIds.addAll(Arrays.asList(config.allowedGroups()));
        ResourceResolver resourceResolver = request.getResourceResolver();
        UserManager userManager = resourceResolver.adaptTo(UserManager.class);
        User user = (User) userManager.getAuthorizable(resourceResolver.getUserID());
        boolean isAllowedMember = false;
        for (Iterator<Group> it = user.memberOf(); it.hasNext(); ) {
            String groupId = it.next().getID();
            if (groupIds.contains(groupId)) {
                isAllowedMember = true;
                break;
            }
        }
        boolean canAccess = config.allowedGroups().length == 0 || user.isAdmin() || isAllowedMember;
        if (!canAccess) {
            throw new IllegalAccessException("You do not have permission to run content sync");
        }
    }
}

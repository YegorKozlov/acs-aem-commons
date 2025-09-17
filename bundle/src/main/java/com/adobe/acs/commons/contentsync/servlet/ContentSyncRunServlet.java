package com.adobe.acs.commons.contentsync.servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;
import javax.servlet.Servlet;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.adobe.acs.commons.contentsync.ContentSyncJobConsumer.JOB_TOPIC;


@Component(service = Servlet.class, immediate = true, property = {
        "sling.servlet.extensions=json",
        "sling.servlet.methods=POST",
        "sling.servlet.selectors=run",
        "sling.servlet.resourceTypes=acs-commons/components/utilities/contentsync",
})
public class ContentSyncRunServlet extends SlingAllMethodsServlet {
    public static final String JOB_ID = "jobId";
    public static final String JOB_STATUS = "status";

    @Reference
    private JobManager jobManager;

    @Override
    protected void doPost(SlingHttpServletRequest slingRequest, SlingHttpServletResponse slingResponse) throws IOException {

        JsonObjectBuilder result = Json.createObjectBuilder();

        Job job = submitJob(slingRequest);
        result.add(JOB_ID, job.getId());
        result.add(JOB_STATUS, job.getJobState().toString());

        slingResponse.setContentType("application/json");
        try (JsonWriter out = Json.createWriter(slingResponse.getWriter())) {
            out.writeObject(result.build());
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
        jobProps.put("startedBy", slingRequest.getResourceResolver().getUserID());
        slingRequest.getParameterMap().forEach((key, value) -> jobProps.put(key, value[0]));
        return jobManager.addJob(JOB_TOPIC, jobProps);
    }
}

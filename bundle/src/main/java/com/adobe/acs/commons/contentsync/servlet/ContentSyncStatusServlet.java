package com.adobe.acs.commons.contentsync.servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component(service = Servlet.class, immediate = true, property = {
        "sling.servlet.extensions=json",
        "sling.servlet.selectors=status",
        "sling.servlet.resourceTypes=acs-commons/components/utilities/contentsync",
})
public class ContentSyncStatusServlet extends SlingSafeMethodsServlet {

    @Reference
    private JobManager jobManager;

    @Override
    protected void doGet(SlingHttpServletRequest slingRequest, SlingHttpServletResponse slingResponse) throws IOException {
        slingResponse.setContentType("application/json");

        JsonObjectBuilder result = Json.createObjectBuilder();
        String jobId = slingRequest.getRequestPathInfo().getSuffix().substring(1);
        if (jobId == null) {
            slingResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
            try (JsonWriter out = Json.createWriter(slingResponse.getWriter())) {
                result.add("error", "jobId is required");
                out.writeObject(result.build());
            }
            return;
        }
        Job job = jobManager.getJobById(jobId);
        for (String key : job.getPropertyNames()) {
            Object val = job.getProperty(key);
            if (val instanceof String[]) {
                JsonArrayBuilder values = Json.createArrayBuilder();
                for (String msg : (String[]) val) values.add(msg);
                result.add(key, values);
            } else {
                result.add(key, val.toString());
            }
        }

        try (JsonWriter out = Json.createWriter(slingResponse.getWriter())) {
            out.writeObject(result.build());
        }
    }
}

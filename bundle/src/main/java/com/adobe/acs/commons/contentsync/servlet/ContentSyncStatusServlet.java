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
        String suffix = slingRequest.getRequestPathInfo().getSuffix();
        if(suffix == null) {
            String msg = "Usage: ...sync.log.txt/jobId, e.g. sync.log.txt/2025/9/17/18/55/cc01d123-127b-4f71-810d-ceeb0c2bd48e_1106";
            slingResponse.getWriter().write(msg);
            slingResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String jobId = suffix.substring(1);

        slingResponse.setContentType("application/json");

        JsonObjectBuilder result = Json.createObjectBuilder();
        Job job = jobManager.getJobById(jobId);
        if(job == null){
            result.add("slingevent:eventId", jobId);
        } else {
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
        }

        try (JsonWriter out = Json.createWriter(slingResponse.getWriter())) {
            out.writeObject(result.build());
        }
    }
}

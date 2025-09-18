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

import static org.apache.sling.event.jobs.Job.PROPERTY_RESULT_MESSAGE;

@Component(service = Servlet.class, immediate = true, property = {
        "sling.servlet.extensions=txt",
        "sling.servlet.selectors=log",
        "sling.servlet.resourceTypes=acs-commons/components/utilities/contentsync",
})
public class ContentSyncLogServlet extends SlingSafeMethodsServlet {

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
        Job job = jobManager.getJobById(jobId);
        String[] progressLog = (String[])job.getProperty(Job.PROPERTY_JOB_PROGRESS_LOG);
        String resultMessage = (String)job.getProperty(PROPERTY_RESULT_MESSAGE);

        slingResponse.setContentType("text/plain");
        if(progressLog != null) {
            for(String msg : progressLog){
                slingResponse.getWriter().println(msg);
            }
        } else if (resultMessage != null) {
            slingResponse.getWriter().println(resultMessage);
        }
    }
}

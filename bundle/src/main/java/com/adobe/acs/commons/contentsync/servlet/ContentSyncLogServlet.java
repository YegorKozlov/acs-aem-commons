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
        "sling.servlet.extensions=txt",
        "sling.servlet.selectors=log",
        "sling.servlet.resourceTypes=acs-commons/components/utilities/contentsync",
})
public class ContentSyncLogServlet extends SlingSafeMethodsServlet {

    @Reference
    private JobManager jobManager;

    @Override
    protected void doGet(SlingHttpServletRequest slingRequest, SlingHttpServletResponse slingResponse) throws IOException {
        slingResponse.setContentType("text/plain");

        String jobId = slingRequest.getRequestPathInfo().getSuffix().substring(1);
        Job job = jobManager.getJobById(jobId);
        String[] progressLog = (String[])job.getProperty(Job.PROPERTY_JOB_PROGRESS_LOG);
        if(progressLog != null) for(String msg : progressLog){
            slingResponse.getWriter().println(msg);
        }
    }
}

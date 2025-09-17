package com.adobe.acs.commons.contentsync;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.adobe.acs.commons.contentsync.ContentSyncJobConsumer.JOB_TOPIC;

@Component(
        service = JobExecutor.class,
        property = {
                JobExecutor.PROPERTY_TOPICS + "=" + JOB_TOPIC,
        }
)
public class ContentSyncJobConsumer implements JobExecutor {
    public static final String JOB_TOPIC = "acs-commons/contentsync/job";

    @Reference
    ContentSyncService syncService;

    @Override
    public JobExecutionResult process(Job job, JobExecutionContext context) {
        String root = (String)job.getProperty("root");
        boolean dryRun = job.getProperty("dryRun") != null;

        long t0 = System.currentTimeMillis();
        try(RemoteInstance remoteInstance = syncService.createRemoteInstance(job)) {
            context.log("{0}", "Building catalog");
            List<CatalogItem> items = syncService.getItemsToSync(job, remoteInstance, context);
            context.initProgress(items.size(), -1);

            // the list of updated resources having child nodes to ensure ordering after update
            Set<String> sortedNodes = new LinkedHashSet<>();

            int count = 1;
            long t1 = System.currentTimeMillis();
            for (CatalogItem item : items) {

                context.log( "[{0}] {1}",count, item.getPath());
                context.log("{0}", item.getMessage());
                if(!dryRun) {
                    syncService.syncItem(item, remoteInstance, context);
                }
                String parentPath = ResourceUtil.getParent(item.getPath());
                if(parentPath.startsWith(root)){
                    sortedNodes.add(parentPath);
                }

                updateProgress(count, items.size(), t1, context);

                ++count;
            }

            if(!dryRun){
                if(job.getProperty("delete") != null){
                    syncService.delete(job, null, context);
                }
                syncService.sort(sortedNodes, remoteInstance, context);
                //syncService.startWorkflows();
            }

            context.log("sync-ed {0} resource(s) in {1} ms", items.size(), System.currentTimeMillis() - t0);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            context.log("{0}", sw.toString());
            return context.result().cancelled();
        }
        return context.result().succeeded();
    }

    void updateProgress(int count, int totalSize, long t0, JobExecutionContext context){
        long remainingCycles = totalSize - count;
        long pace = (System.currentTimeMillis() - t0) / count;
        long estimatedTime = remainingCycles * pace;

        String pct = String.format("%.0f", count*100./totalSize);
        String eta = DurationFormatUtils.formatDurationWords(estimatedTime, true, true);

        context.updateProgress(estimatedTime);
        context.incrementProgressCount(1);
        context.log("{0}%, ETA: {1}", pct, eta);
    }
}

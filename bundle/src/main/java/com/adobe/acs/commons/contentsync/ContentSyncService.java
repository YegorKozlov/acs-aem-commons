package com.adobe.acs.commons.contentsync;

import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;

import java.util.Collection;
import java.util.List;

public interface ContentSyncService {
    List<CatalogItem> getRemoteItems(Job job, RemoteInstance remoteInstance, JobExecutionContext context) throws Exception;

    List<CatalogItem> getItemsToSync(Job job, RemoteInstance remoteInstance, JobExecutionContext context) throws Exception;

    void syncItem(CatalogItem item, RemoteInstance remoteInstance, JobExecutionContext context) throws Exception;

    RemoteInstance createRemoteInstance(Job job) throws Exception;

    void sort(Collection<String> sortedNodes, RemoteInstance remoteInstance, JobExecutionContext context) throws Exception;

    void delete(Job job, List<CatalogItem> remoteItems, JobExecutionContext context) throws Exception;
}

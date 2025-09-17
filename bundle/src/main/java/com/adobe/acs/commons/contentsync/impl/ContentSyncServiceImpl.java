package com.adobe.acs.commons.contentsync.impl;

import com.adobe.acs.commons.adobeio.service.IntegrationService;
import com.adobe.acs.commons.contentsync.*;
import org.apache.sling.api.resource.*;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.jcr.contentloader.ContentImporter;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import javax.jcr.Node;
import javax.jcr.Session;
import javax.json.JsonObject;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ContentSyncServiceImpl implements  ContentSyncService {
    public static final String SERVICE_NAME = "content-sync";

    private final transient Map<String, UpdateStrategy> updateStrategies = Collections.synchronizedMap(new LinkedHashMap<>());

    @Reference
    ContentImporter importer;

    @Reference
    IntegrationService integrationService;

    @Reference
    ResourceResolverFactory resourceResolverFactory;

    @Reference(service = UpdateStrategy.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC)
    protected void bindDeltaStrategy(UpdateStrategy strategy) {
        if (strategy != null) {
            String key = strategy.getClass().getName();
            updateStrategies.put(key, strategy);
        }
    }

    protected void unbindDeltaStrategy(UpdateStrategy strategy) {
        String key = strategy.getClass().getName();
        updateStrategies.remove(key);
    }

    @Override
    public List<CatalogItem> getRemoteItems(Job job, RemoteInstance remoteInstance, JobExecutionContext context) throws Exception {
        String root = (String)job.getProperty("root");
        boolean recursive = job.getProperty("recursive") != null;
        String catalogServlet = (String)job.getProperty("catalogServlet");

        ValueMap generalSettings;
        String strategyPid;

        Map<String, Object> authInfo = Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, SERVICE_NAME);
        try (ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            generalSettings = ConfigurationUtils.getSettingsResource(resourceResolver).getValueMap();
            strategyPid = generalSettings.get(ConfigurationUtils.UPDATE_STRATEGY_KEY, String.class);
        }

        long t0 = System.currentTimeMillis();
        ContentCatalog contentCatalog = new ContentCatalog(remoteInstance, catalogServlet);
        context.log("building catalog from {0}", contentCatalog.getFetchURI(root, strategyPid, recursive));

        String jobId = contentCatalog.startCatalogJob(root, strategyPid, recursive);
        for (; ; ) {
            context.log("{0}", "collecting resources on the remote instance...");
            Thread.sleep(3000L);

            if (contentCatalog.isComplete(jobId)) {
                break;
            }
        }

        List<CatalogItem> items = contentCatalog.getResults();
        context.log("{0} resource(s) fetched in {1} ms", items.size(), (System.currentTimeMillis() - t0) );
        return items;
    }

    @Override
    public List<CatalogItem> getItemsToSync(Job job, RemoteInstance remoteInstance, JobExecutionContext context) throws Exception, GeneralSecurityException, URISyntaxException, InterruptedException {
        boolean incremental = job.getProperty("incremental") != null;

        List<CatalogItem> remoteItems = getRemoteItems(job, remoteInstance, context);
        List<CatalogItem> lst = new ArrayList<>();

        Map<String, Object> authInfo = Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, SERVICE_NAME);
        try (ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            ValueMap generalSettings = ConfigurationUtils.getSettingsResource(resourceResolver).getValueMap();

            String strategyPid = generalSettings.get(ConfigurationUtils.UPDATE_STRATEGY_KEY, String.class);
            UpdateStrategy updateStrategy = getStrategy(strategyPid);
            for(CatalogItem item : remoteItems){
                Resource resource = resourceResolver.getResource(item.getPath());
                if(resource == null || !incremental || updateStrategy.isModified(item, resource)){
                    item.setMessage(updateStrategy.getMessage(item, resource));
                    lst.add(item);
                }
            }
        }
        context.log("{0} resource(s) to sync", lst.size());
        return lst;
    }

    @Override
    public void syncItem(CatalogItem item, RemoteInstance remoteInstance, JobExecutionContext context) throws Exception {
        if(item.getCustomExporter() != null){
            context.log( "{0} has a custom json exporter ({1}}) and cannot be imported", item.getPath(), item.getCustomExporter());
            return;
        }

        Map<String, Object> authInfo = Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, SERVICE_NAME);
        try (ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            Session session = resourceResolver.adaptTo(Session.class);
            ContentReader contentReader = new ContentReader(session);
            ContentSync contentSync = new ContentSync(remoteInstance, resourceResolver, importer);
            try {
                String reqPath = item.getContentUri() ;
                JsonObject json = remoteInstance.getJson(reqPath);

                List<String> binaryProperties = contentReader.collectBinaryProperties(json);
                JsonObject sanitizedJson = contentReader.sanitize(json);

                context.log("\timporting data");
                contentSync.importData(item, sanitizedJson);
                if(!binaryProperties.isEmpty()){
                    context.log("\tcopying {0} binary property(es)", binaryProperties.size() );

                    boolean contentResource = item.hasContentResource();
                    String basePath = item.getPath() + (contentResource ? "/jcr:content" : "");
                    List<String> propertyPaths = binaryProperties.stream().map(p -> basePath + p).collect(Collectors.toList());
                    contentSync.copyBinaries(propertyPaths);
                }

                resourceResolver.commit();
            } catch (Exception e){
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                context.log("{0}", sw.toString());
                resourceResolver.revert();
            }
        }
    }

    /**
     * Get the strategy to build catalog.
     * If pid is null, the first available strategy is used.
     *
     * @param pid the pid of the update strategy
     * @return the update strategy
     */
    UpdateStrategy getStrategy(String pid) {
        UpdateStrategy strategy;
        if(pid == null){
            strategy = updateStrategies.values().iterator().next();
        } else {
            strategy = updateStrategies.get(pid);
            if(strategy == null){
                throw new IllegalArgumentException("Cannot find UpdateStrategy for pid " + pid + "."
                        + " Available strategies: " + updateStrategies.values()
                        .stream().map(s -> s.getClass().getName()).collect(Collectors.toList()));
            }
        }
        return strategy;
    }

    @Override
    public RemoteInstance createRemoteInstance(Job job) throws Exception {
        String cfgPath = (String)job.getProperty("source");

        ValueMap generalSettings;
        SyncHostConfiguration hostConfig;

        Map<String, Object> authInfo = Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, SERVICE_NAME);
        try (ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            generalSettings = new ValueMapDecorator(
                    new HashMap(ConfigurationUtils.getSettingsResource(resourceResolver).getValueMap())
            );
            hostConfig = resourceResolver.getResource(cfgPath).adaptTo(SyncHostConfiguration.class);
        }

        return new RemoteInstance(hostConfig, generalSettings, integrationService);

    }

    @Override
    public void sort(Collection<String> sortedNodes, RemoteInstance remoteInstance, JobExecutionContext context) throws Exception{
        Map<String, Object> authInfo = Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, SERVICE_NAME);
        try (ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            ContentSync contentSync = new ContentSync(remoteInstance, resourceResolver, importer);
            for(String parentPath : sortedNodes){
                Node targetNode = resourceResolver.getResource(parentPath).adaptTo(Node.class);
                context.log("sorting child nodes of {0}", targetNode.getPath() );

                contentSync.sort(targetNode);
            }
        }
    }

    public void delete(Job job, List<CatalogItem> remoteItems, JobExecutionContext context) throws Exception {
        boolean dryRun = job.getProperty("dryRun") != null;
        Map<String, Object> authInfo = Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, SERVICE_NAME);
        try (ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            ValueMap generalSettings = ConfigurationUtils.getSettingsResource(resourceResolver).getValueMap();

            String strategyPid = generalSettings.get(ConfigurationUtils.UPDATE_STRATEGY_KEY, String.class);
            UpdateStrategy updateStrategy = getStrategy(strategyPid);
            Collection<String> remotePaths = remoteItems.stream().map(c -> c.getPath()).collect(Collectors.toList());
            Map<String, Object> jobProperties = job.getPropertyNames().stream().collect(Collectors.toMap(Function.identity(), job::getProperty));
            Collection<String> localPaths = updateStrategy.getItems(jobProperties).stream().map(c -> c.getPath()).collect(Collectors.toList());

            localPaths.removeAll(remotePaths);

            for(String path : localPaths){
                Resource res = resourceResolver.getResource(path);
                if(res != null){
                    context.log("deleting {0}", path);
                    if(!dryRun) {
                        resourceResolver.delete(res);
                    }
                }
            }
        }
    }
}

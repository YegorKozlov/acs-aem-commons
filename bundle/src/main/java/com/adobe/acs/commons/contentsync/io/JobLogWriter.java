package com.adobe.acs.commons.contentsync.io;

import com.adobe.acs.commons.contentsync.ContentSyncService;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.*;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.adobe.acs.commons.contentsync.io.ShardGenerator.DEFAULT_SHARD_SIZE;

public class JobLogWriter implements AutoCloseable {
    static final int BUCKET_SIZE = 1024;
    static final int FLUSH_INTERVAL_MS = 1000;
    static final String DATA_PROPERTY = "messages";

    final ResourceResolverFactory resolverFactory;
    final String nodePath;
    final long lastFlushed;
    final List<String> buffer;
    final ShardGenerator shardGenerator;
    final int bucketSize;
    final int flushInterval;

    public JobLogWriter(ResourceResolverFactory resolverFactory, String nodePath, int bucketSize, int flushInterval, int shardWidth) {
        this.resolverFactory = resolverFactory;
        this.nodePath = nodePath;
        this.lastFlushed = System.currentTimeMillis();
        this.buffer = new ArrayList<>();
        this.shardGenerator = new ShardGenerator(nodePath, shardWidth);
        this.bucketSize = bucketSize;
        this.flushInterval = flushInterval;
    }

    public JobLogWriter(ResourceResolverFactory resolverFactory, String nodePath) {
        this(resolverFactory, nodePath, BUCKET_SIZE, FLUSH_INTERVAL_MS, DEFAULT_SHARD_SIZE);
    }

    public void write(String msg) throws IOException {
        buffer.add(msg);
        flushIfNeeded();
    }

    public void flush() throws IOException {
        if(buffer.isEmpty()) {
            // nothing to flush
            return;
        }
        try (ResourceResolver resourceResolver = resolverFactory.getServiceResourceResolver(ContentSyncService.AUTH_INFO)) {
            String shardNodePath = shardGenerator.getPath();
            Resource res = ResourceUtil.getOrCreateResource(resourceResolver, shardNodePath,
                    JcrConstants.NT_UNSTRUCTURED, JcrResourceConstants.NT_SLING_FOLDER, false);
            res.adaptTo(ModifiableValueMap.class).put(DATA_PROPERTY, buffer.toArray(new String[0]));
            resourceResolver.commit();
        } catch (LoginException e){
            throw new IOException(e);
        }

        if (buffer.size() >= bucketSize) {
            // create next shard
            shardGenerator.nextShard();
            buffer.clear();
        }
    }

    @Override
    public void close() throws IOException {
        flush();
    }

    void flushIfNeeded() throws IOException {
        boolean isBucketFull = buffer.size() >= bucketSize;
        boolean flushTimeout = flushInterval > 0 && System.currentTimeMillis() - lastFlushed > flushInterval;
        if (isBucketFull || flushTimeout) {
            flush();
        }
    }
}

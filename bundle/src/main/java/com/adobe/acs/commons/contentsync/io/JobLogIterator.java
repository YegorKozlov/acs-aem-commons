package com.adobe.acs.commons.contentsync.io;

import org.apache.sling.api.resource.Resource;
import java.util.Iterator;


public class JobLogIterator implements Iterator<String[]> {
    private Resource node;
    private ShardGenerator shardGenerator;
    private Resource shardNode;

    public JobLogIterator(Resource node){
        this.node = node;
        this.shardGenerator = new ShardGenerator(node.getPath());
    }
    public JobLogIterator(Resource node, int shardSize){
        this.node = node;
        this.shardGenerator = new ShardGenerator(node.getPath(), shardSize);
    }

    @Override
    public boolean hasNext() {
        String shardPath = shardGenerator.getPath();
        shardNode =  node.getResourceResolver().getResource(shardPath);
        shardGenerator.nextShard();
        return shardNode != null;
    }

    @Override
    public String[] next() {
        return shardNode.getValueMap().get(JobLogWriter.DATA_PROPERTY, String[].class);
    }
}

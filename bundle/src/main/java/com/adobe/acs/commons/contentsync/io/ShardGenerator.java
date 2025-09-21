package com.adobe.acs.commons.contentsync.io;

public class ShardGenerator {
    static int DEFAULT_SHARD_SIZE = 2;

    final String parentPath;
    final int shardSize; //  characters per level, e.g. 1/2/3 vs 10/21/33
    int counter;

    ShardGenerator(String parentPath){
        this(parentPath, DEFAULT_SHARD_SIZE);
    }
    ShardGenerator(String parentPath, int shardSize){
        this.parentPath = parentPath;
        this.shardSize = shardSize;
    }

    void nextShard(){
        counter++;
    }

    String getShard(){
        String str = String.format("%d", counter);

        StringBuilder out = new StringBuilder();
        StringBuilder shard = new StringBuilder();
        for(char c : str.toCharArray()){
            if(shard.length() == shardSize){
                out.append(shard).append('/');
                shard = new StringBuilder();
            }
            shard.append(c);
        }
        out.append(shard);
        return out.toString();
    }

    String getPath(){
        return parentPath + "/" + getShard();
    }
}

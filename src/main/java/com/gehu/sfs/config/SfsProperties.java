package com.gehu.sfs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SfsProperties {
    @Value("${sfs.disk.blocks}") public int diskBlocks;
    @Value("${sfs.disk.block-size}") public int blockSize;
    @Value("${sfs.storage.dir}") public String storageDir;
    @Value("${sfs.lock.minutes}") public int lockMinutes;
    @Value("${sfs.upload.allowed}") private String allowed;
    public Set<String> allowedExtensions() {
        return Arrays.stream(allowed.split(",")).map(String::trim).collect(Collectors.toSet());
    }
}

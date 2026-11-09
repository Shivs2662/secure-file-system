package com.gehu.sfs.dto;

import com.gehu.sfs.entity.Enums.AllocationType;
import java.time.LocalDateTime;

public final class Views {
    private Views() {}
    public record FileView(Integer id, String name, Integer folderId, Integer ownerId, String ownerName,
                           long sizeBytes, String fileType, AllocationType allocationType, int version,
                           LocalDateTime createdAt, LocalDateTime updatedAt,
                           boolean owner, boolean canRead, boolean canWrite,
                           String lockedBy, boolean lockedByMe, LocalDateTime lockExpiresAt) {}
    public record LockView(Integer fileId, Integer userId, String lockedBy, LocalDateTime acquiredAt, LocalDateTime expiresAt, boolean mine) {}
    public record Download(String name, byte[] bytes, String fileType) {}
    public record PermissionView(Integer userId, String username, boolean canRead, boolean canWrite, boolean owner) {}
    public record UserView(Integer id, String username, String email, String role, LocalDateTime createdAt) {}
}

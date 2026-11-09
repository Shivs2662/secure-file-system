package com.gehu.sfs.service;

import com.gehu.sfs.entity.FileEntity;
import com.gehu.sfs.entity.Permission;
import com.gehu.sfs.exception.ApiException;
import com.gehu.sfs.repository.FileRepository;
import com.gehu.sfs.repository.PermissionRepository;
import org.springframework.stereotype.Service;

/** Central authorization check used by every file endpoint (the frontend is never trusted). */
@Service
public class PermissionService {
    public record Access(boolean owner, boolean read, boolean write) {}
    private final FileRepository files; private final PermissionRepository perms;
    public PermissionService(FileRepository files, PermissionRepository perms) { this.files = files; this.perms = perms; }

    public Access of(FileEntity f, Integer userId) {
        if (f.getOwnerId().equals(userId)) return new Access(true, true, true);
        return perms.findByFileIdAndUserId(f.getId(), userId)
                .map(p -> new Access(false, p.isCanRead(), p.isCanWrite()))
                .orElse(new Access(false, false, false));
    }
    private FileEntity load(Integer id) { return files.findById(id).orElseThrow(() -> ApiException.notFound("File not found")); }

    public FileEntity requireRead(Integer id, Integer userId) {
        FileEntity f = load(id);
        if (!of(f, userId).read()) throw ApiException.forbidden("You do not have read permission on this file");
        return f;
    }
    public FileEntity requireWrite(Integer id, Integer userId) {
        FileEntity f = load(id);
        if (!of(f, userId).write()) throw ApiException.forbidden("You do not have write permission on this file");
        return f;
    }
    public FileEntity requireOwner(Integer id, Integer userId) {
        FileEntity f = load(id);
        if (!f.getOwnerId().equals(userId)) throw ApiException.forbidden("Only the owner can do this");
        return f;
    }
}

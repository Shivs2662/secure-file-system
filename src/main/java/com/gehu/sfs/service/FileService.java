package com.gehu.sfs.service;

import com.gehu.sfs.config.SfsProperties;
import com.gehu.sfs.dto.Requests.ShareReq;
import com.gehu.sfs.dto.Views.*;
import com.gehu.sfs.entity.*;
import com.gehu.sfs.entity.Enums.*;
import com.gehu.sfs.exception.AllocationFailedException;
import com.gehu.sfs.exception.ApiException;
import com.gehu.sfs.repository.*;
import com.gehu.sfs.service.PermissionService.Access;
import com.gehu.sfs.service.allocation.AllocationStrategy;
import com.gehu.sfs.service.allocation.BlockPlan;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Where the OS layer (allocation strategies) meets the DBMS layer (transactions).
 * Upload / edit run as ONE transaction: allocation result + metadata + block map + permission + version
 * either all commit, or all roll back. If two uploads race for the same block, UNIQUE(block_no) rejects one and we retry.
 */
@Service
public class FileService {
    private final FileRepository files; private final FolderRepository folders; private final FileBlockRepository blocks;
    private final PermissionRepository perms; private final FileVersionRepository versions; private final AccessLogRepository access;
    private final FileLockRepository locks; private final UserRepository users;
    private final PermissionService pm; private final FileLockService lockService; private final StorageService storage;
    private final AuditService audit; private final DiskRequestService diskReq; private final SfsProperties props;
    private final List<AllocationStrategy> strategies; private final PlatformTransactionManager txm;

    public FileService(FileRepository files, FolderRepository folders, FileBlockRepository blocks, PermissionRepository perms,
                       FileVersionRepository versions, AccessLogRepository access, FileLockRepository locks, UserRepository users,
                       PermissionService pm, FileLockService lockService, StorageService storage, AuditService audit,
                       DiskRequestService diskReq, SfsProperties props, List<AllocationStrategy> strategies, PlatformTransactionManager txm) {
        this.files = files; this.folders = folders; this.blocks = blocks; this.perms = perms; this.versions = versions;
        this.access = access; this.locks = locks; this.users = users; this.pm = pm; this.lockService = lockService;
        this.storage = storage; this.audit = audit; this.diskReq = diskReq; this.props = props; this.strategies = strategies; this.txm = txm;
    }

    // ---------------------------------------------------------------- helpers
    private AllocationStrategy strategy(AllocationType t) {
        return strategies.stream().filter(s -> s.type() == t).findFirst().orElseThrow(() -> ApiException.bad("Unknown allocation type"));
    }
    private int blocksFor(long bytes) { return (int) Math.max(1, (bytes + props.blockSize - 1) / props.blockSize); }

    private void logAccess(Integer fileId, Integer userId, AccessAction action) {
        AccessLog l = new AccessLog();
        l.setFileId(fileId); l.setUserId(userId); l.setAction(action); l.setAccessedAt(LocalDateTime.now());
        access.save(l);
    }
    private void touchBlocks(Integer userId, Integer fileId) {
        List<Integer> order = new ArrayList<>();
        for (FileBlock b : blocks.findMap(fileId)) order.add(b.getBlockNo());
        diskReq.enqueue(userId, order);
    }
    private static String cleanName(String raw) {
        if (raw == null) throw ApiException.bad("File has no name");
        String n = raw.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).trim();
        if (n.isEmpty() || n.startsWith(".") || n.length() > 150) throw ApiException.bad("Invalid file name");
        return n;
    }
    private static byte[] readBytes(MultipartFile mf) {
        try { return mf.getBytes(); } catch (IOException e) { throw ApiException.bad("Could not read upload"); }
    }
    private static String ext(String name) { int i = name.lastIndexOf('.'); return i < 0 ? "" : name.substring(i + 1).toLowerCase(); }

    public FileView view(FileEntity f, Integer userId) {
        Access a = pm.of(f, userId);
        String owner = users.findById(f.getOwnerId()).map(User::getUsername).orElse("?");
        FileLock l = locks.findFirstByFileIdAndExpiresAtAfter(f.getId(), LocalDateTime.now()).orElse(null);
        String by = l == null ? null : users.findById(l.getUserId()).map(User::getUsername).orElse("?");
        return new FileView(f.getId(), f.getName(), f.getFolderId(), f.getOwnerId(), owner, f.getSizeBytes(), f.getFileType(),
                f.getAllocationType(), f.getCurrentVersion(), f.getCreatedAt(), f.getUpdatedAt(), a.owner(), a.read(), a.write(),
                by, l != null && l.getUserId().equals(userId), l == null ? null : l.getExpiresAt());
    }

    // ---------------------------------------------------------------- upload
    public FileView upload(Integer userId, Integer folderId, MultipartFile mf, AllocationType type) {
        Folder folder = folders.findById(folderId).orElseThrow(() -> ApiException.notFound("Folder not found"));
        if (!folder.getOwnerId().equals(userId)) throw ApiException.forbidden("You can only upload into your own folders");
        String name = cleanName(mf.getOriginalFilename());
        String ext = ext(name);
        if (!props.allowedExtensions().contains(ext)) throw ApiException.bad("File type '." + ext + "' is not allowed. Allowed: " + String.join(", ", new TreeSet<>(props.allowedExtensions())));
        byte[] data = readBytes(mf);
        if (data.length == 0) throw ApiException.bad("File is empty");
        if (files.existsByFolderIdAndName(folderId, name)) throw ApiException.conflict("DUPLICATE_NAME", "A file with this name already exists in the folder");
        int needed = blocksFor(data.length);
        if (needed > props.diskBlocks) throw new AllocationFailedException("File needs " + needed + " blocks; the whole disk has " + props.diskBlocks);
        AllocationStrategy st = strategy(type == null ? AllocationType.CONTIGUOUS : type);

        for (int attempt = 1; ; attempt++) {
            try {
                return new TransactionTemplate(txm).execute(s -> doUpload(userId, folderId, name, ext, data, needed, st));
            } catch (DataIntegrityViolationException race) {
                if (attempt >= 3) throw race;      // another upload grabbed a block first; recompute free list and retry
            }
        }
    }

    private FileView doUpload(Integer userId, Integer folderId, String name, String ext, byte[] data, int needed, AllocationStrategy st) {
        Set<Integer> used = new HashSet<>(blocks.findAllUsedBlockNos());
        List<BlockPlan> plan = st.allocate(needed, used, props.diskBlocks);      // OS decision
        FileEntity f = new FileEntity();
        f.setName(name); f.setFolderId(folderId); f.setOwnerId(userId); f.setSizeBytes((long) data.length);
        f.setFileType(ext); f.setAllocationType(st.type()); f.setCurrentVersion(1);
        f = files.saveAndFlush(f);                                               // DBMS record (trigger writes audit row)
        try {
            saveBlocks(f.getId(), plan);
            Permission p = new Permission(); p.setFileId(f.getId()); p.setUserId(userId); p.setCanRead(true); p.setCanWrite(true);
            perms.save(p);
            FileVersion v = new FileVersion();
            v.setFileId(f.getId()); v.setVersionNo(1); v.setModifiedBy(userId); v.setModifiedAt(LocalDateTime.now()); v.setSizeBytes((long) data.length);
            versions.save(v);
            storage.write(f.getId(), 1, data);
            logAccess(f.getId(), userId, AccessAction.UPLOAD);
            List<Integer> order = new ArrayList<>(); plan.forEach(b -> order.add(b.blockNo()));
            diskReq.enqueue(userId, order);
        } catch (RuntimeException e) {
            storage.deleteAll(f.getId());
            throw e;
        }
        return view(f, userId);
    }

    private void saveBlocks(Integer fileId, List<BlockPlan> plan) {
        List<FileBlock> rows = new ArrayList<>();
        for (BlockPlan bp : plan) {
            FileBlock b = new FileBlock();
            b.setFileId(fileId); b.setBlockNo(bp.blockNo()); b.setSequenceNo(bp.sequenceNo());
            b.setNextBlock(bp.nextBlock()); b.setIndexBlock(bp.indexBlock());
            rows.add(b);
        }
        blocks.saveAllAndFlush(rows);      // flush now so UNIQUE(block_no) violations surface inside the transaction
    }

    // ---------------------------------------------------------------- read
    @Transactional
    public FileView get(Integer userId, Integer id) {
        FileEntity f = pm.requireRead(id, userId);
        logAccess(id, userId, AccessAction.VIEW);
        touchBlocks(userId, id);
        return view(f, userId);
    }

    @Transactional
    public Download download(Integer userId, Integer id) {
        FileEntity f = pm.requireRead(id, userId);
        logAccess(id, userId, AccessAction.DOWNLOAD);
        touchBlocks(userId, id);
        return new Download(f.getName(), storage.read(id, f.getCurrentVersion()), f.getFileType());
    }

    /** Bytes of the current version for the in-browser text editor (also a read access). */
    @Transactional
    public Download content(Integer userId, Integer id) { return download(userId, id); }

    @Transactional(readOnly = true)
    public List<FileBlock> blockMap(Integer userId, Integer id) {
        pm.requireRead(id, userId);
        return blocks.findMap(id);
    }

    @Transactional(readOnly = true)
    public List<AccessLog> accessHistory(Integer userId, Integer id) {
        pm.requireRead(id, userId);
        return access.findTop50ByFileIdOrderByAccessedAtDescIdDesc(id);
    }

    @Transactional(readOnly = true)
    public List<FileView> sharedWithMe(Integer userId) {
        List<FileView> out = new ArrayList<>();
        for (FileEntity f : files.findSharedWith(userId)) out.add(view(f, userId));
        return out;
    }

    // ---------------------------------------------------------------- edit / versions
    public FileView edit(Integer userId, Integer id, byte[] data, boolean release) {
        pm.requireWrite(id, userId);
        lockService.assertHeld(id, userId);
        if (data.length == 0) throw ApiException.bad("File is empty");
        if (blocksFor(data.length) > props.diskBlocks) throw new AllocationFailedException("File too large for the simulated disk");
        return newVersionWithRetry(userId, id, data, release, AccessAction.EDIT);
    }

    public FileView restore(Integer userId, Integer id, int versionNo) {
        FileEntity f = pm.requireWrite(id, userId);
        lockService.assertHeld(id, userId);
        versions.findByFileIdAndVersionNo(id, versionNo).orElseThrow(() -> ApiException.notFound("Version not found"));
        byte[] old = storage.read(id, versionNo);
        FileView v = newVersionWithRetry(userId, id, old, false, AccessAction.EDIT);
        audit.log(userId, "VERSION_RESTORED", "file_id=" + id + " restored v" + versionNo + " as v" + v.version());
        return v;
    }

    private FileView newVersionWithRetry(Integer userId, Integer id, byte[] data, boolean release, AccessAction action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return new TransactionTemplate(txm).execute(s -> {
                    FileView v = doNewVersion(userId, id, data, action);
                    if (release) lockService.releaseQuietly(id, userId);
                    return v;
                });
            } catch (DataIntegrityViolationException race) {
                if (attempt >= 3) throw race;
            }
        }
    }

    private FileView doNewVersion(Integer userId, Integer id, byte[] data, AccessAction action) {
        FileEntity f = files.findById(id).orElseThrow(() -> ApiException.notFound("File not found"));
        int next = f.getCurrentVersion() + 1;
        blocks.deleteByFile(id);                                            // free the old blocks...
        Set<Integer> used = new HashSet<>(blocks.findAllUsedBlockNos());
        List<BlockPlan> plan = strategy(f.getAllocationType()).allocate(blocksFor(data.length), used, props.diskBlocks);   // ...and re-allocate; failure rolls everything back
        saveBlocks(id, plan);
        f.setSizeBytes((long) data.length); f.setCurrentVersion(next);
        files.saveAndFlush(f);                                              // trigger logs FILE_VERSIONED
        FileVersion v = new FileVersion();
        v.setFileId(id); v.setVersionNo(next); v.setModifiedBy(userId); v.setModifiedAt(LocalDateTime.now()); v.setSizeBytes((long) data.length);
        versions.save(v);
        storage.write(id, next, data);
        logAccess(id, userId, action);
        List<Integer> order = new ArrayList<>(); plan.forEach(b -> order.add(b.blockNo()));
        diskReq.enqueue(userId, order);
        return view(f, userId);
    }

    @Transactional(readOnly = true)
    public List<FileVersion> versions(Integer userId, Integer id) {
        pm.requireRead(id, userId);
        return versions.findByFileIdOrderByVersionNoDesc(id);
    }

    // ---------------------------------------------------------------- delete / move
    @Transactional
    public void delete(Integer userId, Integer id) {
        FileEntity f = pm.requireOwner(id, userId);
        audit.log(userId, "FILE_DELETED", "File \"" + f.getName() + "\" (id=" + id + ") deleted");
        locks.deleteByFile(id);
        files.delete(f);               // FK ON DELETE CASCADE removes blocks, permissions, versions, access logs
        files.flush();
        storage.deleteAll(id);
    }

    @Transactional
    public FileView move(Integer userId, Integer id, Integer folderId) {
        FileEntity f = pm.requireOwner(id, userId);
        Folder dest = folders.findById(folderId).orElseThrow(() -> ApiException.notFound("Destination folder not found"));
        if (!dest.getOwnerId().equals(userId)) throw ApiException.forbidden("Destination is not your folder");
        if (files.existsByFolderIdAndName(folderId, f.getName())) throw ApiException.conflict("DUPLICATE_NAME", "Destination already has a file with this name");
        f.setFolderId(folderId);                  // metadata-only: blocks are untouched
        files.save(f);
        audit.log(userId, "FILE_MOVED", "file_id=" + id + " moved to folder_id=" + folderId);
        return view(f, userId);
    }

    // ---------------------------------------------------------------- sharing
    @Transactional(readOnly = true)
    public List<PermissionView> permissions(Integer userId, Integer id) {
        FileEntity f = pm.requireOwner(id, userId);
        List<PermissionView> out = new ArrayList<>();
        for (Permission p : perms.findByFileId(id)) {
            String uname = users.findById(p.getUserId()).map(User::getUsername).orElse("?");
            out.add(new PermissionView(p.getUserId(), uname, p.isCanRead(), p.isCanWrite(), p.getUserId().equals(f.getOwnerId())));
        }
        return out;
    }

    @Transactional
    public void share(Integer userId, Integer id, ShareReq req) {
        FileEntity f = pm.requireOwner(id, userId);
        User target = users.findByUsername(req.username()).orElseThrow(() -> ApiException.notFound("No user named '" + req.username() + "'"));
        if (target.getId().equals(f.getOwnerId())) throw ApiException.bad("The owner already has full access");
        boolean read = req.canRead() || req.canWrite();       // write implies read
        if (!read) throw ApiException.bad("Grant at least read access (or use revoke)");
        Permission p = perms.findByFileIdAndUserId(id, target.getId()).orElseGet(Permission::new);
        p.setFileId(id); p.setUserId(target.getId()); p.setCanRead(true); p.setCanWrite(req.canWrite());
        perms.save(p);                                        // trigger writes the PERMISSION_* audit row
        logAccess(id, userId, AccessAction.SHARE);
    }

    @Transactional
    public void revoke(Integer userId, Integer id, Integer targetUserId) {
        FileEntity f = pm.requireOwner(id, userId);
        if (targetUserId.equals(f.getOwnerId())) throw ApiException.bad("Cannot revoke the owner");
        Permission p = perms.findByFileIdAndUserId(id, targetUserId).orElseThrow(() -> ApiException.notFound("User has no access"));
        perms.delete(p);
        audit.log(userId, "PERMISSION_REVOKED", "user_id=" + targetUserId + " lost access to file_id=" + id);
    }
}

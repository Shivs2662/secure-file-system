package com.gehu.sfs.service;

import com.gehu.sfs.config.SfsProperties;
import com.gehu.sfs.dto.Views.LockView;
import com.gehu.sfs.entity.Enums.LockType;
import com.gehu.sfs.entity.FileEntity;
import com.gehu.sfs.entity.FileLock;
import com.gehu.sfs.exception.ApiException;
import com.gehu.sfs.repository.FileLockRepository;
import com.gehu.sfs.repository.FileRepository;
import com.gehu.sfs.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Application-level locking backed by MySQL transactions.
 * The critical section "check for an active lock, then insert one" is made atomic by taking
 * SELECT ... FOR UPDATE on the file's row first, so two racing requests are serialized by InnoDB.
 */
@Service
public class FileLockService {
    private final FileRepository files; private final FileLockRepository locks; private final UserRepository users;
    private final PermissionService perms; private final AuditService audit; private final SfsProperties props;
    private final PlatformTransactionManager txm;

    public FileLockService(FileRepository files, FileLockRepository locks, UserRepository users, PermissionService perms,
                           AuditService audit, SfsProperties props, PlatformTransactionManager txm) {
        this.files = files; this.locks = locks; this.users = users; this.perms = perms; this.audit = audit; this.props = props; this.txm = txm;
    }

    private String name(Integer userId) { return users.findById(userId).map(u -> u.getUsername()).orElse("?"); }
    private LockView view(FileLock l, Integer me) {
        return new LockView(l.getFileId(), l.getUserId(), name(l.getUserId()), l.getAcquiredAt(), l.getExpiresAt(), l.getUserId().equals(me));
    }

    public LockView acquire(Integer userId, Integer fileId) {
        perms.requireWrite(fileId, userId);
        return new TransactionTemplate(txm).execute(status -> {
            files.lockById(fileId).orElseThrow(() -> ApiException.notFound("File not found"));   // FOR UPDATE = mutex
            LocalDateTime now = LocalDateTime.now();
            locks.deleteExpired(now);
            Optional<FileLock> cur = locks.findFirstByFileIdAndExpiresAtAfter(fileId, now);
            if (cur.isPresent()) {
                FileLock l = cur.get();
                if (!l.getUserId().equals(userId))
                    throw ApiException.conflict("FILE_LOCKED", "File is locked by " + name(l.getUserId()) + " until " + l.getExpiresAt());
                l.setExpiresAt(now.plusMinutes(props.lockMinutes));       // re-acquire = renew
                return view(locks.save(l), userId);
            }
            FileLock l = new FileLock();
            l.setFileId(fileId); l.setUserId(userId); l.setLockType(LockType.WRITE);
            l.setAcquiredAt(now); l.setExpiresAt(now.plusMinutes(props.lockMinutes));
            l = locks.save(l);
            audit.log(userId, "LOCK_ACQUIRED", "WRITE lock on file_id=" + fileId);
            return view(l, userId);
        });
    }

    @Transactional(readOnly = true)
    public Optional<LockView> status(Integer userId, Integer fileId) {
        perms.requireRead(fileId, userId);
        return locks.findFirstByFileIdAndExpiresAtAfter(fileId, LocalDateTime.now()).map(l -> view(l, userId));
    }

    /** Holder releases; the file owner may force-release (e.g. a colleague forgot). */
    @Transactional
    public void release(Integer userId, Integer fileId) {
        FileEntity f = perms.requireRead(fileId, userId);
        Optional<FileLock> cur = locks.findFirstByFileIdAndExpiresAtAfter(fileId, LocalDateTime.now());
        if (cur.isEmpty()) return;
        FileLock l = cur.get();
        if (!l.getUserId().equals(userId) && !f.getOwnerId().equals(userId))
            throw ApiException.forbidden("Only the lock holder or the file owner can release this lock");
        locks.delete(l);
        audit.log(userId, "LOCK_RELEASED", "WRITE lock on file_id=" + fileId + (l.getUserId().equals(userId) ? "" : " (forced by owner)"));
    }

    /** Editing is only allowed while the caller holds an active write lock. */
    public void assertHeld(Integer fileId, Integer userId) {
        FileLock l = locks.findFirstByFileIdAndExpiresAtAfter(fileId, LocalDateTime.now()).orElse(null);
        if (l == null) throw ApiException.conflict("LOCK_REQUIRED", "Acquire the write lock before editing this file");
        if (!l.getUserId().equals(userId)) throw ApiException.conflict("FILE_LOCKED", "File is locked by " + name(l.getUserId()));
    }

    /** Called inside an existing transaction right after a successful save. */
    public void releaseQuietly(Integer fileId, Integer userId) {
        locks.findFirstByFileIdAndExpiresAtAfter(fileId, LocalDateTime.now())
             .filter(l -> l.getUserId().equals(userId)).ifPresent(locks::delete);
    }

    /** Crash recovery: locks whose holder disappeared are removed after their timeout. */
    @Scheduled(fixedDelay = 60_000)
    public void cleanupExpired() {
        Integer n = new TransactionTemplate(txm).execute(s -> locks.deleteExpired(LocalDateTime.now()));
        if (n != null && n > 0) audit.log(null, "LOCK_EXPIRED", n + " expired lock(s) cleaned up");
    }
}

package com.gehu.sfs.service;

import com.gehu.sfs.entity.FileEntity;
import com.gehu.sfs.entity.Folder;
import com.gehu.sfs.exception.ApiException;
import com.gehu.sfs.repository.FileRepository;
import com.gehu.sfs.repository.FolderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
public class FolderService {
    private final FolderRepository folders; private final FileRepository files; private final StorageService storage; private final AuditService audit;
    public FolderService(FolderRepository folders, FileRepository files, StorageService storage, AuditService audit) {
        this.folders = folders; this.files = files; this.storage = storage; this.audit = audit;
    }

    public Folder requireOwned(Integer id, Integer userId) {
        Folder f = folders.findById(id).orElseThrow(() -> ApiException.notFound("Folder not found"));
        if (!f.getOwnerId().equals(userId)) throw ApiException.forbidden("This is not your folder");
        return f;
    }

    @Transactional
    public Folder create(Integer userId, String name, Integer parentId) {
        requireOwned(parentId, userId);
        String n = name.trim();
        if (folders.existsByParentIdAndNameAndOwnerId(parentId, n, userId)) throw ApiException.conflict("DUPLICATE_NAME", "A folder with this name already exists here");
        Folder f = new Folder(); f.setName(n); f.setOwnerId(userId); f.setParentId(parentId);
        f = folders.save(f);
        audit.log(userId, "FOLDER_CREATED", "Folder \"" + n + "\" (id=" + f.getId() + ")");
        return f;
    }

    @Transactional(readOnly = true)
    public List<Folder> tree(Integer userId) { return folders.findByOwnerIdOrderByName(userId); }

    @Transactional(readOnly = true)
    public List<Folder> breadcrumb(Folder f) {
        List<Folder> path = new ArrayList<>();
        Folder cur = f;
        while (cur != null) { path.add(0, cur); cur = cur.getParentId() == null ? null : folders.findById(cur.getParentId()).orElse(null); }
        return path;
    }

    private void collect(Integer folderId, List<Integer> folderIds, List<Integer> fileIds) {
        folderIds.add(folderId);
        for (FileEntity f : files.findByFolderIdOrderByName(folderId)) fileIds.add(f.getId());
        for (Folder c : folders.findByParentIdOrderByName(folderId)) collect(c.getId(), folderIds, fileIds);
    }

    @Transactional
    public void delete(Integer userId, Integer id, boolean force) {
        Folder f = requireOwned(id, userId);
        if (f.getParentId() == null) throw ApiException.bad("The root folder cannot be deleted");
        List<Integer> folderIds = new ArrayList<>(), fileIds = new ArrayList<>();
        collect(id, folderIds, fileIds);
        if ((folderIds.size() > 1 || !fileIds.isEmpty()) && !force)
            throw ApiException.conflict("FOLDER_NOT_EMPTY", "Folder contains " + (folderIds.size() - 1) + " sub-folder(s) and " + fileIds.size() + " file(s)");
        audit.log(userId, "FOLDER_DELETED", "Folder \"" + f.getName() + "\" (id=" + id + ") with " + fileIds.size() + " file(s)");
        folders.delete(f);        // DB cascade removes sub-folders, files, blocks, permissions, versions
        folders.flush();
        fileIds.forEach(storage::deleteAll);
    }
}

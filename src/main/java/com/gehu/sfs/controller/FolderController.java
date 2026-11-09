package com.gehu.sfs.controller;

import com.gehu.sfs.dto.Requests.FolderReq;
import com.gehu.sfs.dto.Views.FileView;
import com.gehu.sfs.entity.FileEntity;
import com.gehu.sfs.entity.Folder;
import com.gehu.sfs.repository.FileRepository;
import com.gehu.sfs.repository.FolderRepository;
import com.gehu.sfs.security.CurrentUser;
import com.gehu.sfs.service.FileService;
import com.gehu.sfs.service.FolderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/folders")
public class FolderController {
    private final FolderService folders; private final FolderRepository folderRepo; private final FileRepository fileRepo; private final FileService files;
    public FolderController(FolderService f, FolderRepository fr, FileRepository fl, FileService fs) { folders = f; folderRepo = fr; fileRepo = fl; files = fs; }

    @PostMapping
    public ResponseEntity<Folder> create(@Valid @RequestBody FolderReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(folders.create(CurrentUser.id(), req.name(), req.parentId()));
    }

    @GetMapping("/tree")
    public List<Folder> tree() { return folders.tree(CurrentUser.id()); }

    @GetMapping("/{id}/contents")
    public Map<String, Object> contents(@PathVariable Integer id) {
        Integer uid = CurrentUser.id();
        Folder f = folders.requireOwned(id, uid);
        List<FileView> fileViews = new ArrayList<>();
        for (FileEntity fe : fileRepo.findByFolderIdOrderByName(id)) fileViews.add(files.view(fe, uid));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("folder", f); out.put("breadcrumb", folders.breadcrumb(f));
        out.put("folders", folderRepo.findByParentIdOrderByName(id)); out.put("files", fileViews);
        return out;
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable Integer id, @RequestParam(defaultValue = "false") boolean force) {
        folders.delete(CurrentUser.id(), id, force);
        return Map.of("status", "deleted");
    }
}

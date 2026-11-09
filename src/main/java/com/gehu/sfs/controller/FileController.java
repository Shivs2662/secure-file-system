package com.gehu.sfs.controller;

import com.gehu.sfs.dto.Requests.MoveReq;
import com.gehu.sfs.dto.Requests.ShareReq;
import com.gehu.sfs.dto.Views.*;
import com.gehu.sfs.entity.AccessLog;
import com.gehu.sfs.entity.Enums.AllocationType;
import com.gehu.sfs.entity.FileBlock;
import com.gehu.sfs.entity.FileVersion;
import com.gehu.sfs.security.CurrentUser;
import com.gehu.sfs.service.FileLockService;
import com.gehu.sfs.service.FileService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService files; private final FileLockService locks;
    public FileController(FileService f, FileLockService l) { files = f; locks = l; }

    @PostMapping("/upload")
    public ResponseEntity<FileView> upload(@RequestParam("file") MultipartFile file, @RequestParam Integer folderId,
                                           @RequestParam(defaultValue = "CONTIGUOUS") AllocationType allocationType) {
        return ResponseEntity.status(HttpStatus.CREATED).body(files.upload(CurrentUser.id(), folderId, file, allocationType));
    }

    @GetMapping("/shared") public List<FileView> shared() { return files.sharedWithMe(CurrentUser.id()); }
    @GetMapping("/{id}") public FileView get(@PathVariable Integer id) { return files.get(CurrentUser.id(), id); }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Integer id) {
        Download d = files.download(CurrentUser.id(), id);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.name(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.APPLICATION_OCTET_STREAM).body(d.bytes());
    }

    /** Plain text of the current version for the in-browser editor. */
    @GetMapping("/{id}/content")
    public ResponseEntity<String> content(@PathVariable Integer id) {
        Download d = files.content(CurrentUser.id(), id);
        return ResponseEntity.ok().contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .header("X-Content-Type-Options", "nosniff").body(new String(d.bytes(), StandardCharsets.UTF_8));
    }

    /** Save a new version (needs write permission AND the write lock). Lock is released unless release=false. */
    @PutMapping("/{id}")
    public FileView edit(@PathVariable Integer id, @RequestParam("file") MultipartFile file,
                         @RequestParam(defaultValue = "true") boolean release) throws IOException {
        return files.edit(CurrentUser.id(), id, file.getBytes(), release);
    }

    @DeleteMapping("/{id}") public Map<String, String> delete(@PathVariable Integer id) { files.delete(CurrentUser.id(), id); return Map.of("status", "deleted"); }
    @PutMapping("/{id}/move") public FileView move(@PathVariable Integer id, @Valid @RequestBody MoveReq req) { return files.move(CurrentUser.id(), id, req.folderId()); }

    @GetMapping("/{id}/blocks") public List<FileBlock> blocks(@PathVariable Integer id) { return files.blockMap(CurrentUser.id(), id); }
    @GetMapping("/{id}/access-logs") public List<AccessLog> history(@PathVariable Integer id) { return files.accessHistory(CurrentUser.id(), id); }

    // ---- versions
    @GetMapping("/{id}/versions") public List<FileVersion> versions(@PathVariable Integer id) { return files.versions(CurrentUser.id(), id); }
    @PostMapping("/{id}/versions/{v}/restore") public FileView restore(@PathVariable Integer id, @PathVariable int v) { return files.restore(CurrentUser.id(), id, v); }

    // ---- sharing / permissions
    @GetMapping("/{id}/permissions") public List<PermissionView> permissions(@PathVariable Integer id) { return files.permissions(CurrentUser.id(), id); }
    @PostMapping("/{id}/share") public Map<String, String> share(@PathVariable Integer id, @Valid @RequestBody ShareReq req) { files.share(CurrentUser.id(), id, req); return Map.of("status", "shared"); }
    @DeleteMapping("/{id}/share/{userId}") public Map<String, String> revoke(@PathVariable Integer id, @PathVariable Integer userId) { files.revoke(CurrentUser.id(), id, userId); return Map.of("status", "revoked"); }

    // ---- locks
    @PostMapping("/{id}/lock") public LockView lock(@PathVariable Integer id) { return locks.acquire(CurrentUser.id(), id); }
    @DeleteMapping("/{id}/lock") public Map<String, String> unlock(@PathVariable Integer id) { locks.release(CurrentUser.id(), id); return Map.of("status", "released"); }
    @GetMapping("/{id}/lock")
    public Object lockStatus(@PathVariable Integer id) {
        return locks.status(CurrentUser.id(), id).<Object>map(l -> l).orElse(Map.of("locked", false));
    }
}

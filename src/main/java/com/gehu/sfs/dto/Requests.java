package com.gehu.sfs.dto;

import com.gehu.sfs.entity.Enums.*;
import jakarta.validation.constraints.*;
import java.util.List;

public final class Requests {
    private Requests() {}
    public record Register(
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "may contain letters, digits, _ . - only") String username,
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {}
    public record Login(@NotBlank String username, @NotBlank String password) {}
    public record FolderReq(@NotBlank @Size(max = 100) @Pattern(regexp = "^[^/\\\\]+$", message = "must not contain slashes") String name, @NotNull Integer parentId) {}
    public record MoveReq(@NotNull Integer folderId) {}
    public record ShareReq(@NotBlank String username, boolean canRead, boolean canWrite) {}
    public record RoleReq(@NotNull Role role) {}
    public record AllocSim(@NotNull AllocationType type, @Min(1) @Max(100) int blocks) {}
    public record ScheduleReq(@NotNull Algorithm algorithm, @Min(0) int headStart, List<Integer> requests, boolean useQueue, boolean consume) {}
}

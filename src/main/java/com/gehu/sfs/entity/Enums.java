package com.gehu.sfs.entity;

public final class Enums {
    private Enums() {}
    public enum Role { ADMIN, USER }
    public enum AllocationType { CONTIGUOUS, LINKED, INDEXED }
    public enum LockType { READ, WRITE }
    public enum AccessAction { VIEW, DOWNLOAD, UPLOAD, EDIT, DELETE, SHARE }
    public enum RequestStatus { PENDING, SERVICED }
    public enum Algorithm { FCFS, SSTF, SCAN, CSCAN }
}

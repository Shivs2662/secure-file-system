-- Secure File Management & Storage System  |  MySQL 8+
-- Run this whole script in MySQL Workbench (File > Run SQL Script).
DROP DATABASE IF EXISTS secure_file_system;
CREATE DATABASE secure_file_system CHARACTER SET utf8mb4;
USE secure_file_system;

CREATE TABLE Users (
  user_id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  email VARCHAR(100) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role ENUM('ADMIN','USER') NOT NULL DEFAULT 'USER',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE Folders (
  folder_id INT AUTO_INCREMENT PRIMARY KEY,
  folder_name VARCHAR(100) NOT NULL,
  owner_id INT NOT NULL,
  parent_folder_id INT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (owner_id) REFERENCES Users(user_id),
  FOREIGN KEY (parent_folder_id) REFERENCES Folders(folder_id) ON DELETE CASCADE,
  INDEX idx_folders_parent (parent_folder_id),
  INDEX idx_folders_owner (owner_id)
);

CREATE TABLE Files (
  file_id INT AUTO_INCREMENT PRIMARY KEY,
  file_name VARCHAR(150) NOT NULL,
  folder_id INT NOT NULL,
  owner_id INT NOT NULL,
  file_size_bytes BIGINT NOT NULL,
  file_type VARCHAR(20) NOT NULL,
  allocation_type ENUM('CONTIGUOUS','LINKED','INDEXED') NOT NULL,
  current_version INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (folder_id) REFERENCES Folders(folder_id) ON DELETE CASCADE,
  FOREIGN KEY (owner_id) REFERENCES Users(user_id),
  INDEX idx_files_owner (owner_id),
  INDEX idx_files_folder (folder_id)
);

-- UNIQUE(block_no): one physical block can belong to only one file (DBMS enforces what the OS module decides)
CREATE TABLE File_Blocks (
  block_row_id INT AUTO_INCREMENT PRIMARY KEY,
  file_id INT NOT NULL,
  block_no INT NOT NULL,
  sequence_no INT NULL,
  next_block INT NULL,
  is_index_block BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE KEY uq_block_no (block_no),
  FOREIGN KEY (file_id) REFERENCES Files(file_id) ON DELETE CASCADE,
  INDEX idx_blocks_file (file_id)
);

CREATE TABLE Permissions (
  permission_id INT AUTO_INCREMENT PRIMARY KEY,
  file_id INT NOT NULL,
  user_id INT NOT NULL,
  can_read BOOLEAN NOT NULL DEFAULT TRUE,
  can_write BOOLEAN NOT NULL DEFAULT FALSE,
  granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_file_user (file_id, user_id),
  FOREIGN KEY (file_id) REFERENCES Files(file_id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
);

CREATE TABLE File_Locks (
  lock_id INT AUTO_INCREMENT PRIMARY KEY,
  file_id INT NOT NULL,
  user_id INT NOT NULL,
  lock_type ENUM('READ','WRITE') NOT NULL DEFAULT 'WRITE',
  acquired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL,
  FOREIGN KEY (file_id) REFERENCES Files(file_id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE,
  INDEX idx_locks_file (file_id)
);

CREATE TABLE File_Versions (
  version_id INT AUTO_INCREMENT PRIMARY KEY,
  file_id INT NOT NULL,
  version_no INT NOT NULL,
  modified_by INT NOT NULL,
  modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  file_size_bytes BIGINT NOT NULL,
  UNIQUE KEY uq_file_version (file_id, version_no),
  FOREIGN KEY (file_id) REFERENCES Files(file_id) ON DELETE CASCADE,
  FOREIGN KEY (modified_by) REFERENCES Users(user_id)
);

CREATE TABLE Access_Logs (
  log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  file_id INT NOT NULL,
  user_id INT NOT NULL,
  action ENUM('VIEW','DOWNLOAD','UPLOAD','EDIT','DELETE','SHARE') NOT NULL,
  accessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (file_id) REFERENCES Files(file_id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES Users(user_id),
  INDEX idx_access_file_time (file_id, accessed_at)
);

CREATE TABLE Disk_Requests (
  request_id INT AUTO_INCREMENT PRIMARY KEY,
  block_no INT NOT NULL,
  requested_by INT NOT NULL,
  requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status ENUM('PENDING','SERVICED') NOT NULL DEFAULT 'PENDING',
  FOREIGN KEY (requested_by) REFERENCES Users(user_id) ON DELETE CASCADE,
  INDEX idx_req_status (status)
);

CREATE TABLE Scheduling_Runs (
  run_id INT AUTO_INCREMENT PRIMARY KEY,
  algorithm ENUM('FCFS','SSTF','SCAN','CSCAN') NOT NULL,
  head_start INT NOT NULL,
  run_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  total_head_movement INT NOT NULL,
  INDEX idx_runs_algo (algorithm)
);

CREATE TABLE Scheduling_Results (
  result_id INT AUTO_INCREMENT PRIMARY KEY,
  run_id INT NOT NULL,
  step_no INT NOT NULL,
  block_no INT NOT NULL,
  distance_from_prev INT NOT NULL,
  UNIQUE KEY uq_run_step (run_id, step_no),
  FOREIGN KEY (run_id) REFERENCES Scheduling_Runs(run_id) ON DELETE CASCADE
);

CREATE TABLE Audit_Logs (
  audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NULL,
  event_type VARCHAR(50) NOT NULL,
  description VARCHAR(255) NOT NULL,
  event_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE SET NULL,
  INDEX idx_audit_user (user_id)
);

-- ===================== VIEWS =====================
CREATE VIEW vw_file_access_report AS
SELECT f.file_id, f.file_name, u.username AS accessed_by, al.action, al.accessed_at
FROM Access_Logs al JOIN Files f ON f.file_id = al.file_id JOIN Users u ON u.user_id = al.user_id;

CREATE VIEW vw_storage_status AS
SELECT u.user_id, u.username, COUNT(f.file_id) AS total_files, COALESCE(SUM(f.file_size_bytes),0) AS bytes_used
FROM Users u LEFT JOIN Files f ON f.owner_id = u.user_id GROUP BY u.user_id, u.username;

CREATE VIEW vw_scheduling_performance AS
SELECT algorithm, COUNT(*) AS total_runs, AVG(total_head_movement) AS avg_head_movement,
       MIN(total_head_movement) AS best_case, MAX(total_head_movement) AS worst_case
FROM Scheduling_Runs GROUP BY algorithm;

-- ===================== STORED PROCEDURES =====================
DELIMITER //
CREATE PROCEDURE sp_upload_file(IN p_name VARCHAR(150), IN p_folder INT, IN p_owner INT,
  IN p_size BIGINT, IN p_type VARCHAR(20), IN p_alloc ENUM('CONTIGUOUS','LINKED','INDEXED'))
BEGIN
  DECLARE fid INT;
  START TRANSACTION;
    INSERT INTO Files (file_name, folder_id, owner_id, file_size_bytes, file_type, allocation_type)
      VALUES (p_name, p_folder, p_owner, p_size, p_type, p_alloc);
    SET fid = LAST_INSERT_ID();
    INSERT INTO Permissions (file_id, user_id, can_read, can_write) VALUES (fid, p_owner, TRUE, TRUE);
    INSERT INTO File_Versions (file_id, version_no, modified_by, file_size_bytes) VALUES (fid, 1, p_owner, p_size);
  COMMIT;
  SELECT fid AS file_id;
END //

CREATE PROCEDURE sp_acquire_lock(IN p_file INT, IN p_user INT, OUT p_ok BOOLEAN)
BEGIN
  DECLARE cnt INT DEFAULT 0;
  START TRANSACTION;
    SELECT file_id INTO @dummy FROM Files WHERE file_id = p_file FOR UPDATE;   -- mutex on the file row
    DELETE FROM File_Locks WHERE file_id = p_file AND expires_at <= NOW();
    SELECT COUNT(*) INTO cnt FROM File_Locks WHERE file_id = p_file;
    IF cnt = 0 THEN
      INSERT INTO File_Locks (file_id, user_id, lock_type, expires_at)
        VALUES (p_file, p_user, 'WRITE', DATE_ADD(NOW(), INTERVAL 30 MINUTE));
      SET p_ok = TRUE;
    ELSE SET p_ok = FALSE; END IF;
  COMMIT;
END //

CREATE PROCEDURE sp_release_lock(IN p_file INT, IN p_user INT)
BEGIN
  DELETE FROM File_Locks WHERE file_id = p_file AND user_id = p_user;
END //

CREATE PROCEDURE sp_record_scheduling_run(IN p_algo ENUM('FCFS','SSTF','SCAN','CSCAN'), IN p_head INT, IN p_total INT)
BEGIN
  INSERT INTO Scheduling_Runs (algorithm, head_start, total_head_movement) VALUES (p_algo, p_head, p_total);
  SELECT LAST_INSERT_ID() AS run_id;
END //

-- ===================== TRIGGERS =====================
CREATE TRIGGER trg_file_insert_audit AFTER INSERT ON Files FOR EACH ROW
BEGIN
  INSERT INTO Audit_Logs (user_id, event_type, description)
  VALUES (NEW.owner_id, 'FILE_CREATED', CONCAT('File "', NEW.file_name, '" (id=', NEW.file_id, ') created'));
END //

CREATE TRIGGER trg_permission_insert_audit AFTER INSERT ON Permissions FOR EACH ROW
BEGIN
  INSERT INTO Audit_Logs (user_id, event_type, description)
  VALUES (NEW.user_id, 'PERMISSION_GRANTED',
    CONCAT('user_id=', NEW.user_id, ' read=', NEW.can_read, ' write=', NEW.can_write, ' on file_id=', NEW.file_id));
END //

CREATE TRIGGER trg_permission_update_audit AFTER UPDATE ON Permissions FOR EACH ROW
BEGIN
  INSERT INTO Audit_Logs (user_id, event_type, description)
  VALUES (NEW.user_id, 'PERMISSION_CHANGED',
    CONCAT('user_id=', NEW.user_id, ' now read=', NEW.can_read, ' write=', NEW.can_write, ' on file_id=', NEW.file_id));
END //

CREATE TRIGGER trg_file_update_audit AFTER UPDATE ON Files FOR EACH ROW
BEGIN
  IF NEW.current_version <> OLD.current_version THEN
    INSERT INTO Audit_Logs (user_id, event_type, description)
    VALUES (NEW.owner_id, 'FILE_VERSIONED',
      CONCAT('file_id=', NEW.file_id, ' version ', OLD.current_version, ' -> ', NEW.current_version));
  END IF;
END //
DELIMITER ;

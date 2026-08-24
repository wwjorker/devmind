package com.devmind.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

@TableName("ai_bad_case")
public class AiBadCase {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String sourceType;
    private String sourceRef;
    private Long feedbackId;
    private Long askLogId;
    private String askSnapshotJson;
    private String chunkSnapshotJson;
    private String trustedSourceJson;
    private Integer promptSchemaVersion;
    private String rootCause;
    private String diagnosisJson;
    private String status;
    @Version
    private Integer statusVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public Long getFeedbackId() { return feedbackId; }
    public void setFeedbackId(Long feedbackId) { this.feedbackId = feedbackId; }
    public Long getAskLogId() { return askLogId; }
    public void setAskLogId(Long askLogId) { this.askLogId = askLogId; }
    public String getAskSnapshotJson() { return askSnapshotJson; }
    public void setAskSnapshotJson(String askSnapshotJson) { this.askSnapshotJson = askSnapshotJson; }
    public String getChunkSnapshotJson() { return chunkSnapshotJson; }
    public void setChunkSnapshotJson(String chunkSnapshotJson) { this.chunkSnapshotJson = chunkSnapshotJson; }
    public String getTrustedSourceJson() { return trustedSourceJson; }
    public void setTrustedSourceJson(String trustedSourceJson) { this.trustedSourceJson = trustedSourceJson; }
    public Integer getPromptSchemaVersion() { return promptSchemaVersion; }
    public void setPromptSchemaVersion(Integer promptSchemaVersion) { this.promptSchemaVersion = promptSchemaVersion; }
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public String getDiagnosisJson() { return diagnosisJson; }
    public void setDiagnosisJson(String diagnosisJson) { this.diagnosisJson = diagnosisJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getStatusVersion() { return statusVersion; }
    public void setStatusVersion(Integer statusVersion) { this.statusVersion = statusVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

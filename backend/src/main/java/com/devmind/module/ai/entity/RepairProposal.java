package com.devmind.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

@TableName("repair_proposal")
public class RepairProposal {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long badCaseId;
    private String proposalType;
    private Long targetDocumentId;
    private Integer baseVersionNo;
    private String diffJson;
    private String evidenceJson;
    private String counterevidenceJson;
    private String impactJson;
    private String regressionPlanJson;
    private String reviewerVerdict;
    private String reviewerFindingsJson;
    private Integer revisionNo;
    private String status;
    private String approvalIdempotencyKey;
    private String approvedDiffJson;
    private String decisionComment;
    private String executionIdempotencyKey;
    private String executionResultJson;
    private String errorCode;
    private String errorMessage;
    private String idempotencyKey;
    @Version
    private Integer lockVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getBadCaseId() { return badCaseId; }
    public void setBadCaseId(Long badCaseId) { this.badCaseId = badCaseId; }
    public String getProposalType() { return proposalType; }
    public void setProposalType(String proposalType) { this.proposalType = proposalType; }
    public Long getTargetDocumentId() { return targetDocumentId; }
    public void setTargetDocumentId(Long targetDocumentId) { this.targetDocumentId = targetDocumentId; }
    public Integer getBaseVersionNo() { return baseVersionNo; }
    public void setBaseVersionNo(Integer baseVersionNo) { this.baseVersionNo = baseVersionNo; }
    public String getDiffJson() { return diffJson; }
    public void setDiffJson(String diffJson) { this.diffJson = diffJson; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
    public String getCounterevidenceJson() { return counterevidenceJson; }
    public void setCounterevidenceJson(String counterevidenceJson) { this.counterevidenceJson = counterevidenceJson; }
    public String getImpactJson() { return impactJson; }
    public void setImpactJson(String impactJson) { this.impactJson = impactJson; }
    public String getRegressionPlanJson() { return regressionPlanJson; }
    public void setRegressionPlanJson(String regressionPlanJson) { this.regressionPlanJson = regressionPlanJson; }
    public String getReviewerVerdict() { return reviewerVerdict; }
    public void setReviewerVerdict(String reviewerVerdict) { this.reviewerVerdict = reviewerVerdict; }
    public String getReviewerFindingsJson() { return reviewerFindingsJson; }
    public void setReviewerFindingsJson(String reviewerFindingsJson) { this.reviewerFindingsJson = reviewerFindingsJson; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer revisionNo) { this.revisionNo = revisionNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApprovalIdempotencyKey() { return approvalIdempotencyKey; }
    public void setApprovalIdempotencyKey(String approvalIdempotencyKey) { this.approvalIdempotencyKey = approvalIdempotencyKey; }
    public String getApprovedDiffJson() { return approvedDiffJson; }
    public void setApprovedDiffJson(String approvedDiffJson) { this.approvedDiffJson = approvedDiffJson; }
    public String getDecisionComment() { return decisionComment; }
    public void setDecisionComment(String decisionComment) { this.decisionComment = decisionComment; }
    public String getExecutionIdempotencyKey() { return executionIdempotencyKey; }
    public void setExecutionIdempotencyKey(String executionIdempotencyKey) { this.executionIdempotencyKey = executionIdempotencyKey; }
    public String getExecutionResultJson() { return executionResultJson; }
    public void setExecutionResultJson(String executionResultJson) { this.executionResultJson = executionResultJson; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Integer getLockVersion() { return lockVersion; }
    public void setLockVersion(Integer lockVersion) { this.lockVersion = lockVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

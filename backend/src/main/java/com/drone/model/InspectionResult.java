package com.drone.model;

public class InspectionResult {
    private String targetId;
    private long completedAt;
    private String status;
    private Integer photos;
    private Integer videoDuration;
    private String notes;
    private Boolean anomalyDetected;

    public InspectionResult() {}

    public InspectionResult(String targetId, long completedAt, String status) {
        this.targetId = targetId;
        this.completedAt = completedAt;
        this.status = status;
    }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public long getCompletedAt() { return completedAt; }
    public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getPhotos() { return photos; }
    public void setPhotos(Integer photos) { this.photos = photos; }
    public Integer getVideoDuration() { return videoDuration; }
    public void setVideoDuration(Integer videoDuration) { this.videoDuration = videoDuration; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Boolean getAnomalyDetected() { return anomalyDetected; }
    public void setAnomalyDetected(Boolean anomalyDetected) { this.anomalyDetected = anomalyDetected; }
}

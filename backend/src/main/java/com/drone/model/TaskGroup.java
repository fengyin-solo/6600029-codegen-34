package com.drone.model;

import java.util.List;

public class TaskGroup {
    private String id;
    private String name;
    private String description;
    private long createdAt;
    private long updatedAt;
    private List<TaskSegment> segments;
    private int currentSegmentIndex;
    private double overallProgress;
    private String overallStatus;
    private TotalStats totalStats;
    private int anomalyCount;

    public static class TotalStats {
        private double totalDistance;
        private double totalEstimatedTime;
        private double totalBatteryUsage;
        private double completedDistance;
        private double completedTime;
        private double usedBattery;
        private int targetsTotal;
        private int targetsCompleted;
        private int segmentsTotal;
        private int segmentsCompleted;

        public double getTotalDistance() { return totalDistance; }
        public void setTotalDistance(double totalDistance) { this.totalDistance = totalDistance; }
        public double getTotalEstimatedTime() { return totalEstimatedTime; }
        public void setTotalEstimatedTime(double totalEstimatedTime) { this.totalEstimatedTime = totalEstimatedTime; }
        public double getTotalBatteryUsage() { return totalBatteryUsage; }
        public void setTotalBatteryUsage(double totalBatteryUsage) { this.totalBatteryUsage = totalBatteryUsage; }
        public double getCompletedDistance() { return completedDistance; }
        public void setCompletedDistance(double completedDistance) { this.completedDistance = completedDistance; }
        public double getCompletedTime() { return completedTime; }
        public void setCompletedTime(double completedTime) { this.completedTime = completedTime; }
        public double getUsedBattery() { return usedBattery; }
        public void setUsedBattery(double usedBattery) { this.usedBattery = usedBattery; }
        public int getTargetsTotal() { return targetsTotal; }
        public void setTargetsTotal(int targetsTotal) { this.targetsTotal = targetsTotal; }
        public int getTargetsCompleted() { return targetsCompleted; }
        public void setTargetsCompleted(int targetsCompleted) { this.targetsCompleted = targetsCompleted; }
        public int getSegmentsTotal() { return segmentsTotal; }
        public void setSegmentsTotal(int segmentsTotal) { this.segmentsTotal = segmentsTotal; }
        public int getSegmentsCompleted() { return segmentsCompleted; }
        public void setSegmentsCompleted(int segmentsCompleted) { this.segmentsCompleted = segmentsCompleted; }
    }

    public TaskGroup() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public List<TaskSegment> getSegments() { return segments; }
    public void setSegments(List<TaskSegment> segments) { this.segments = segments; }
    public int getCurrentSegmentIndex() { return currentSegmentIndex; }
    public void setCurrentSegmentIndex(int currentSegmentIndex) { this.currentSegmentIndex = currentSegmentIndex; }
    public double getOverallProgress() { return overallProgress; }
    public void setOverallProgress(double overallProgress) { this.overallProgress = overallProgress; }
    public String getOverallStatus() { return overallStatus; }
    public void setOverallStatus(String overallStatus) { this.overallStatus = overallStatus; }
    public TotalStats getTotalStats() { return totalStats; }
    public void setTotalStats(TotalStats totalStats) { this.totalStats = totalStats; }
    public int getAnomalyCount() { return anomalyCount; }
    public void setAnomalyCount(int anomalyCount) { this.anomalyCount = anomalyCount; }
}

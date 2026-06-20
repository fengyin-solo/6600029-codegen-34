package com.drone.model;

import java.util.List;

public class TaskSegment {
    private String id;
    private String name;
    private String description;
    private int order;
    private List<InspectionTarget> targets;
    private List<Waypoint> waypoints;
    private String status;
    private double progress;
    private SegmentStats stats;
    private List<InspectionResult> results;
    private Long startedAt;
    private Long completedAt;

    public TaskSegment() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    public List<InspectionTarget> getTargets() { return targets; }
    public void setTargets(List<InspectionTarget> targets) { this.targets = targets; }
    public List<Waypoint> getWaypoints() { return waypoints; }
    public void setWaypoints(List<Waypoint> waypoints) { this.waypoints = waypoints; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getProgress() { return progress; }
    public void setProgress(double progress) { this.progress = progress; }
    public SegmentStats getStats() { return stats; }
    public void setStats(SegmentStats stats) { this.stats = stats; }
    public List<InspectionResult> getResults() { return results; }
    public void setResults(List<InspectionResult> results) { this.results = results; }
    public Long getStartedAt() { return startedAt; }
    public void setStartedAt(Long startedAt) { this.startedAt = startedAt; }
    public Long getCompletedAt() { return completedAt; }
    public void setCompletedAt(Long completedAt) { this.completedAt = completedAt; }
}

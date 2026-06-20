package com.drone.model;

public class SegmentStats {
    private double totalDistance;
    private double estimatedTime;
    private double batteryUsage;
    private double completedDistance;
    private double completedTime;
    private double usedBattery;
    private int targetsTotal;
    private int targetsCompleted;

    public SegmentStats() {}

    public double getTotalDistance() { return totalDistance; }
    public void setTotalDistance(double totalDistance) { this.totalDistance = totalDistance; }
    public double getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(double estimatedTime) { this.estimatedTime = estimatedTime; }
    public double getBatteryUsage() { return batteryUsage; }
    public void setBatteryUsage(double batteryUsage) { this.batteryUsage = batteryUsage; }
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
}

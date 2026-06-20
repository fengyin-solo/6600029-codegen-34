package com.drone.model;

public class InspectionTarget {
    private String id;
    private String name;
    private String description;
    private double lat;
    private double lng;
    private double altitude;
    private String action;
    private Integer hoverDuration;
    private String priority;
    private String[] tags;

    public InspectionTarget() {}

    public InspectionTarget(String id, String name, double lat, double lng, double altitude, String action, String priority) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.altitude = altitude;
        this.action = action;
        this.priority = priority;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }
    public double getAltitude() { return altitude; }
    public void setAltitude(double altitude) { this.altitude = altitude; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Integer getHoverDuration() { return hoverDuration; }
    public void setHoverDuration(Integer hoverDuration) { this.hoverDuration = hoverDuration; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags; }
}

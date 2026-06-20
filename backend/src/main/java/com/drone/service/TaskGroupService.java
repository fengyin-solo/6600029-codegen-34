package com.drone.service;

import com.drone.model.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TaskGroupService {

    private final Map<String, TaskGroup> taskGroupStore = new HashMap<>();
    private final RouteService routeService;

    public TaskGroupService(RouteService routeService) {
        this.routeService = routeService;
    }

    public TaskGroup createTaskGroup(String name, String description) {
        TaskGroup group = new TaskGroup();
        String id = "tg-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6);
        group.setId(id);
        group.setName(name);
        group.setDescription(description);
        group.setCreatedAt(System.currentTimeMillis());
        group.setUpdatedAt(System.currentTimeMillis());
        group.setSegments(new ArrayList<>());
        group.setCurrentSegmentIndex(0);
        group.setOverallProgress(0);
        group.setOverallStatus("pending");
        group.setAnomalyCount(0);
        group.setTotalStats(new TaskGroup.TotalStats());
        taskGroupStore.put(id, group);
        return group;
    }

    public TaskGroup addSegment(String groupId, String name, String description,
                                List<InspectionTarget> targets) {
        TaskGroup group = taskGroupStore.get(groupId);
        if (group == null) throw new IllegalArgumentException("TaskGroup not found: " + groupId);

        TaskSegment segment = new TaskSegment();
        String segId = "seg-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6);
        segment.setId(segId);
        segment.setName(name);
        segment.setDescription(description);
        segment.setOrder(group.getSegments().size());
        segment.setTargets(targets != null ? targets : new ArrayList<>());
        segment.setWaypoints(new ArrayList<>());
        segment.setStatus("pending");
        segment.setProgress(0);
        segment.setStats(new SegmentStats());
        segment.setResults(new ArrayList<>());

        if (targets != null) {
            SegmentStats stats = calculateSegmentStats(targets);
            segment.setStats(stats);
            segment.setWaypoints(generateSegmentWaypoints(targets));
        }

        group.getSegments().add(segment);
        recalculateTotalStats(group);
        group.setUpdatedAt(System.currentTimeMillis());
        return group;
    }

    public TaskGroup removeSegment(String groupId, String segmentId) {
        TaskGroup group = taskGroupStore.get(groupId);
        if (group == null) throw new IllegalArgumentException("TaskGroup not found: " + groupId);

        group.getSegments().removeIf(seg -> seg.getId().equals(segmentId));
        for (int i = 0; i < group.getSegments().size(); i++) {
            group.getSegments().get(i).setOrder(i);
        }
        recalculateTotalStats(group);
        group.setUpdatedAt(System.currentTimeMillis());
        return group;
    }

    public TaskGroup reorderSegments(String groupId, List<String> segmentOrderIds) {
        TaskGroup group = taskGroupStore.get(groupId);
        if (group == null) throw new IllegalArgumentException("TaskGroup not found: " + groupId);

        List<TaskSegment> reordered = new ArrayList<>();
        for (String id : segmentOrderIds) {
            group.getSegments().stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .ifPresent(reordered::add);
        }
        for (int i = 0; i < reordered.size(); i++) {
            reordered.get(i).setOrder(i);
        }
        group.setSegments(reordered);
        group.setUpdatedAt(System.currentTimeMillis());
        return group;
    }

    public TaskGroup startSegment(String groupId, String segmentId) {
        TaskGroup group = taskGroupStore.get(groupId);
        if (group == null) throw new IllegalArgumentException("TaskGroup not found: " + groupId);

        for (TaskSegment seg : group.getSegments()) {
            if (seg.getId().equals(segmentId)) {
                seg.setStatus("running");
                seg.setStartedAt(System.currentTimeMillis());
                group.setCurrentSegmentIndex(seg.getOrder());
                group.setOverallStatus("running");
            }
        }
        group.setUpdatedAt(System.currentTimeMillis());
        return group;
    }

    public TaskGroup updateSegmentProgress(String groupId, String segmentId,
                                           double progress, InspectionResult result) {
        TaskGroup group = taskGroupStore.get(groupId);
        if (group == null) throw new IllegalArgumentException("TaskGroup not found: " + groupId);

        for (TaskSegment seg : group.getSegments()) {
            if (seg.getId().equals(segmentId)) {
                seg.setProgress(Math.min(100, progress));
                if (result != null) {
                    seg.getResults().add(result);
                    if (Boolean.TRUE.equals(result.getAnomalyDetected())) {
                        group.setAnomalyCount(group.getAnomalyCount() + 1);
                    }
                    seg.getStats().setTargetsCompleted(
                        (int) seg.getResults().stream()
                            .map(InspectionResult::getTargetId).distinct().count()
                    );
                }
                double ratio = progress / 100.0;
                seg.getStats().setCompletedDistance(seg.getStats().getTotalDistance() * ratio);
                seg.getStats().setCompletedTime(seg.getStats().getEstimatedTime() * ratio);
                seg.getStats().setUsedBattery(seg.getStats().getBatteryUsage() * ratio);

                if (progress >= 100) {
                    seg.setStatus("completed");
                    seg.setCompletedAt(System.currentTimeMillis());
                }
            }
        }
        recalculateTotalStats(group);
        calculateOverallProgress(group);
        updateOverallStatus(group);
        group.setUpdatedAt(System.currentTimeMillis());
        return group;
    }

    public TaskGroup pauseSegment(String groupId, String segmentId) {
        TaskGroup group = taskGroupStore.get(groupId);
        if (group == null) throw new IllegalArgumentException("TaskGroup not found: " + groupId);
        group.getSegments().stream()
            .filter(s -> s.getId().equals(segmentId))
            .findFirst()
            .ifPresent(s -> s.setStatus("paused"));
        group.setOverallStatus("paused");
        group.setUpdatedAt(System.currentTimeMillis());
        return group;
    }

    public TaskGroup failSegment(String groupId, String segmentId, String reason) {
        TaskGroup group = taskGroupStore.get(groupId);
        if (group == null) throw new IllegalArgumentException("TaskGroup not found: " + groupId);
        group.getSegments().stream()
            .filter(s -> s.getId().equals(segmentId))
            .findFirst()
            .ifPresent(s -> {
                s.setStatus("failed");
                s.setCompletedAt(System.currentTimeMillis());
            });
        group.setOverallStatus("failed");
        group.setUpdatedAt(System.currentTimeMillis());
        return group;
    }

    public TaskGroup getTaskGroup(String id) {
        return taskGroupStore.get(id);
    }

    public List<TaskGroup> listTaskGroups() {
        return new ArrayList<>(taskGroupStore.values());
    }

    public void deleteTaskGroup(String id) {
        taskGroupStore.remove(id);
    }

    private SegmentStats calculateSegmentStats(List<InspectionTarget> targets) {
        SegmentStats stats = new SegmentStats();
        if (targets == null || targets.isEmpty()) return stats;

        double totalDist = 0;
        for (int i = 0; i < targets.size() - 1; i++) {
            InspectionTarget t1 = targets.get(i);
            InspectionTarget t2 = targets.get(i + 1);
            totalDist += haversine(t1.getLat(), t1.getLng(), t2.getLat(), t2.getLng());
        }
        stats.setTotalDistance(totalDist);
        double avgSpeed = 10;
        stats.setEstimatedTime(totalDist / avgSpeed + targets.size() * 30);
        stats.setBatteryUsage(Math.min(100, (stats.getEstimatedTime() / 3600) * 30));
        stats.setTargetsTotal(targets.size());
        stats.setTargetsCompleted(0);
        stats.setCompletedDistance(0);
        stats.setCompletedTime(0);
        stats.setUsedBattery(0);
        return stats;
    }

    private List<Waypoint> generateSegmentWaypoints(List<InspectionTarget> targets) {
        List<Waypoint> waypoints = new ArrayList<>();
        int idx = 0;
        for (InspectionTarget t : targets) {
            waypoints.add(new Waypoint(
                "wp-seg-" + t.getId() + "-" + idx,
                t.getLat(), t.getLng(), t.getAltitude(),
                10, t.getAction()
            ));
            idx++;
        }
        return waypoints;
    }

    private void recalculateTotalStats(TaskGroup group) {
        TaskGroup.TotalStats ts = new TaskGroup.TotalStats();
        int segmentsTotal = group.getSegments().size();
        int segmentsCompleted = 0;
        int targetsTotal = 0;
        int targetsCompleted = 0;

        for (TaskSegment seg : group.getSegments()) {
            SegmentStats ss = seg.getStats();
            ts.setTotalDistance(ts.getTotalDistance() + ss.getTotalDistance());
            ts.setTotalEstimatedTime(ts.getTotalEstimatedTime() + ss.getEstimatedTime());
            ts.setTotalBatteryUsage(ts.getTotalBatteryUsage() + ss.getBatteryUsage());
            ts.setCompletedDistance(ts.getCompletedDistance() + ss.getCompletedDistance());
            ts.setCompletedTime(ts.getCompletedTime() + ss.getCompletedTime());
            ts.setUsedBattery(ts.getUsedBattery() + ss.getUsedBattery());
            targetsTotal += ss.getTargetsTotal();
            targetsCompleted += ss.getTargetsCompleted();
            if ("completed".equals(seg.getStatus())) segmentsCompleted++;
        }
        ts.setSegmentsTotal(segmentsTotal);
        ts.setSegmentsCompleted(segmentsCompleted);
        ts.setTargetsTotal(targetsTotal);
        ts.setTargetsCompleted(targetsCompleted);
        group.setTotalStats(ts);
    }

    private void calculateOverallProgress(TaskGroup group) {
        if (group.getSegments().isEmpty()) {
            group.setOverallProgress(0);
            return;
        }
        double total = 0;
        for (TaskSegment seg : group.getSegments()) {
            total += seg.getProgress();
        }
        group.setOverallProgress(total / group.getSegments().size());
    }

    private void updateOverallStatus(TaskGroup group) {
        boolean allCompleted = group.getSegments().stream()
            .allMatch(s -> "completed".equals(s.getStatus()));
        if (allCompleted && !group.getSegments().isEmpty()) {
            group.setOverallStatus("completed");
            return;
        }
        boolean anyFailed = group.getSegments().stream()
            .anyMatch(s -> "failed".equals(s.getStatus()));
        if (anyFailed) {
            group.setOverallStatus("failed");
            return;
        }
        boolean anyRunning = group.getSegments().stream()
            .anyMatch(s -> "running".equals(s.getStatus()));
        if (anyRunning) {
            group.setOverallStatus("running");
            return;
        }
        boolean anyPaused = group.getSegments().stream()
            .anyMatch(s -> "paused".equals(s.getStatus()));
        if (anyPaused) {
            group.setOverallStatus("paused");
            return;
        }
        group.setOverallStatus("pending");
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public List<InspectionTarget> generateMockTargets(String area) {
        List<InspectionTarget> targets = new ArrayList<>();
        Random rand = new Random();
        double baseLat = 39.88 + rand.nextDouble() * 0.04;
        double baseLng = 116.37 + rand.nextDouble() * 0.04;
        String[] actions = {"photo", "video", "hover", "none"};
        String[] priorities = {"low", "medium", "high"};
        for (int i = 0; i < 5; i++) {
            InspectionTarget t = new InspectionTarget(
                "tgt-" + area + "-" + i + "-" + System.currentTimeMillis(),
                "巡检目标-" + area + "-" + (i + 1),
                baseLat + rand.nextDouble() * 0.01 - 0.005,
                baseLng + rand.nextDouble() * 0.01 - 0.005,
                80 + rand.nextInt(60),
                actions[rand.nextInt(actions.length)],
                priorities[rand.nextInt(priorities.length)]
            );
            t.setHoverDuration(30 + rand.nextInt(60));
            t.setDescription("该目标需要进行" + t.getAction() + "巡检操作");
            targets.add(t);
        }
        return targets;
    }
}

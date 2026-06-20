package com.drone.controller;

import com.drone.model.InspectionResult;
import com.drone.model.InspectionTarget;
import com.drone.model.TaskGroup;
import com.drone.service.TaskGroupService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/taskgroups")
@CrossOrigin(origins = "*")
public class TaskGroupController {

    private final TaskGroupService taskGroupService;

    public TaskGroupController(TaskGroupService taskGroupService) {
        this.taskGroupService = taskGroupService;
    }

    @PostMapping
    public TaskGroup createTaskGroup(@RequestBody Map<String, Object> request) {
        String name = (String) request.getOrDefault("name", "未命名任务组");
        String description = (String) request.getOrDefault("description", "");
        return taskGroupService.createTaskGroup(name, description);
    }

    @GetMapping
    public List<TaskGroup> listTaskGroups() {
        return taskGroupService.listTaskGroups();
    }

    @GetMapping("/{id}")
    public TaskGroup getTaskGroup(@PathVariable String id) {
        return taskGroupService.getTaskGroup(id);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> deleteTaskGroup(@PathVariable String id) {
        taskGroupService.deleteTaskGroup(id);
        return Map.of("status", "success", "message", "Task group deleted");
    }

    @PostMapping("/{groupId}/segments")
    public TaskGroup addSegment(@PathVariable String groupId,
                                @RequestBody Map<String, Object> request) {
        String name = (String) request.getOrDefault("name", "未命名子任务");
        String description = (String) request.getOrDefault("description", "");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> targetsData = (List<Map<String, Object>>) request.getOrDefault("targets", new ArrayList<>());
        List<InspectionTarget> targets = new ArrayList<>();
        for (Map<String, Object> t : targetsData) {
            InspectionTarget target = new InspectionTarget();
            target.setId((String) t.getOrDefault("id", "tgt-" + System.currentTimeMillis()));
            target.setName((String) t.getOrDefault("name", "未命名目标"));
            target.setDescription((String) t.getOrDefault("description", ""));
            target.setLat(((Number) t.getOrDefault("lat", 0)).doubleValue());
            target.setLng(((Number) t.getOrDefault("lng", 0)).doubleValue());
            target.setAltitude(((Number) t.getOrDefault("altitude", 100)).doubleValue());
            target.setAction((String) t.getOrDefault("action", "none"));
            target.setHoverDuration(t.get("hoverDuration") != null ? ((Number) t.get("hoverDuration")).intValue() : null);
            target.setPriority((String) t.getOrDefault("priority", "medium"));
            if (t.get("tags") != null) {
                @SuppressWarnings("unchecked")
                List<String> tags = (List<String>) t.get("tags");
                target.setTags(tags.toArray(new String[0]));
            }
            targets.add(target);
        }
        return taskGroupService.addSegment(groupId, name, description, targets);
    }

    @DeleteMapping("/{groupId}/segments/{segmentId}")
    public TaskGroup removeSegment(@PathVariable String groupId,
                                   @PathVariable String segmentId) {
        return taskGroupService.removeSegment(groupId, segmentId);
    }

    @PostMapping("/{groupId}/segments/reorder")
    public TaskGroup reorderSegments(@PathVariable String groupId,
                                     @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> orderIds = (List<String>) request.getOrDefault("order", new ArrayList<>());
        return taskGroupService.reorderSegments(groupId, orderIds);
    }

    @PostMapping("/{groupId}/segments/{segmentId}/start")
    public TaskGroup startSegment(@PathVariable String groupId,
                                  @PathVariable String segmentId) {
        return taskGroupService.startSegment(groupId, segmentId);
    }

    @PostMapping("/{groupId}/segments/{segmentId}/progress")
    public TaskGroup updateSegmentProgress(@PathVariable String groupId,
                                           @PathVariable String segmentId,
                                           @RequestBody Map<String, Object> request) {
        double progress = ((Number) request.getOrDefault("progress", 0)).doubleValue();
        InspectionResult result = null;
        if (request.get("result") != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rData = (Map<String, Object>) request.get("result");
            result = new InspectionResult();
            result.setTargetId((String) rData.getOrDefault("targetId", ""));
            result.setCompletedAt(((Number) rData.getOrDefault("completedAt", System.currentTimeMillis())).longValue());
            result.setStatus((String) rData.getOrDefault("status", "success"));
            if (rData.get("photos") != null) {
                result.setPhotos(((Number) rData.get("photos")).intValue());
            }
            if (rData.get("videoDuration") != null) {
                result.setVideoDuration(((Number) rData.get("videoDuration")).intValue());
            }
            result.setNotes((String) rData.getOrDefault("notes", ""));
            result.setAnomalyDetected((Boolean) rData.getOrDefault("anomalyDetected", false));
        }
        return taskGroupService.updateSegmentProgress(groupId, segmentId, progress, result);
    }

    @PostMapping("/{groupId}/segments/{segmentId}/pause")
    public TaskGroup pauseSegment(@PathVariable String groupId,
                                  @PathVariable String segmentId) {
        return taskGroupService.pauseSegment(groupId, segmentId);
    }

    @PostMapping("/{groupId}/segments/{segmentId}/fail")
    public TaskGroup failSegment(@PathVariable String groupId,
                                 @PathVariable String segmentId,
                                 @RequestBody(required = false) Map<String, Object> request) {
        String reason = request != null ? (String) request.getOrDefault("reason", "") : "";
        return taskGroupService.failSegment(groupId, segmentId, reason);
    }

    @GetMapping("/mock-targets/{area}")
    public List<InspectionTarget> generateMockTargets(@PathVariable String area) {
        return taskGroupService.generateMockTargets(area);
    }
}

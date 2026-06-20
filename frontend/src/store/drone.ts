import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type {
  Waypoint,
  NoFlyZone,
  TerrainPoint,
  FlightPlan,
  DroneConfig,
  TaskGroup,
  TaskSegment,
  InspectionTarget,
  InspectionResult,
  SegmentStatus,
} from '../types';
import {
  aStarPathfind,
  rrtPathfind,
  smoothPath,
  calculateFlightStats,
  checkTerrainCollision,
  exportKML,
  mockNoFlyZones,
  mockTerrainData,
} from '../utils/pathfinding';

function genId(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function haversine(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6371000;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLng / 2) *
      Math.sin(dLng / 2);
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export const useDroneStore = defineStore('drone', () => {
  const waypoints = ref<Waypoint[]>([]);
  const noFlyZones = ref<NoFlyZone[]>([]);
  const terrainData = ref<TerrainPoint[]>([]);
  const currentPlan = ref<FlightPlan | null>(null);
  const selectedAlgorithm = ref<'astar' | 'rrt'>('astar');
  const isSimulating = ref(false);
  const simProgress = ref(0);
  const mapCenter = ref<[number, number]>([39.9, 116.4]);

  const droneConfig = ref<DroneConfig>({
    maxAltitude: 500,
    maxSpeed: 20,
    batteryCapacity: 5000,
    consumptionRate: 100,
    safeDistance: 30,
  });

  const taskGroups = ref<TaskGroup[]>([]);
  const currentTaskGroupId = ref<string | null>(null);
  const selectedSegmentId = ref<string | null>(null);

  // ─── Task Group Actions ──────────────────────────────────────────────────
  const currentTaskGroup = computed<TaskGroup | null>(() => {
    if (!currentTaskGroupId.value) return null;
    return taskGroups.value.find((g) => g.id === currentTaskGroupId.value) || null;
  });

  const selectedSegment = computed<TaskSegment | null>(() => {
    if (!selectedSegmentId.value || !currentTaskGroup.value) return null;
    return currentTaskGroup.value.segments.find((s) => s.id === selectedSegmentId.value) || null;
  });

  function createTaskGroup(name: string, description = ''): TaskGroup {
    const group: TaskGroup = {
      id: genId('tg'),
      name,
      description,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      segments: [],
      currentSegmentIndex: 0,
      overallProgress: 0,
      overallStatus: 'pending',
      totalStats: {
        totalDistance: 0,
        totalEstimatedTime: 0,
        totalBatteryUsage: 0,
        completedDistance: 0,
        completedTime: 0,
        usedBattery: 0,
        targetsTotal: 0,
        targetsCompleted: 0,
        segmentsTotal: 0,
        segmentsCompleted: 0,
      },
      anomalyCount: 0,
    };
    taskGroups.value.push(group);
    currentTaskGroupId.value = group.id;
    return group;
  }

  function selectTaskGroup(id: string | null) {
    currentTaskGroupId.value = id;
    selectedSegmentId.value = null;
  }

  function deleteTaskGroup(id: string) {
    taskGroups.value = taskGroups.value.filter((g) => g.id !== id);
    if (currentTaskGroupId.value === id) {
      currentTaskGroupId.value = null;
      selectedSegmentId.value = null;
    }
  }

  function calculateSegmentStats(targets: InspectionTarget[]) {
    let totalDist = 0;
    for (let i = 0; i < targets.length - 1; i++) {
      const t1 = targets[i];
      const t2 = targets[i + 1];
      totalDist += haversine(t1.lat, t1.lng, t2.lat, t2.lng);
    }
    const estimatedTime = totalDist / 10 + targets.length * 30;
    const batteryUsage = Math.min(100, (estimatedTime / 3600) * 30);
    return {
      totalDistance: totalDist,
      estimatedTime,
      batteryUsage,
      completedDistance: 0,
      completedTime: 0,
      usedBattery: 0,
      targetsTotal: targets.length,
      targetsCompleted: 0,
    };
  }

  function generateSegmentWaypoints(targets: InspectionTarget[]): Waypoint[] {
    return targets.map((t) => ({
      id: genId('wp-seg'),
      lat: t.lat,
      lng: t.lng,
      altitude: t.altitude,
      speed: 10,
      action: t.action,
    }));
  }

  function addSegment(
    groupId: string,
    name: string,
    description = '',
    targets: InspectionTarget[] = []
  ): TaskSegment | null {
    const group = taskGroups.value.find((g) => g.id === groupId);
    if (!group) return null;

    const segment: TaskSegment = {
      id: genId('seg'),
      name,
      description,
      order: group.segments.length,
      targets,
      waypoints: generateSegmentWaypoints(targets),
      status: 'pending',
      progress: 0,
      stats: calculateSegmentStats(targets),
      results: [],
    };
    group.segments.push(segment);
    recalcGroupTotals(group);
    group.updatedAt = Date.now();
    return segment;
  }

  function removeSegment(groupId: string, segmentId: string) {
    const group = taskGroups.value.find((g) => g.id === groupId);
    if (!group) return;
    group.segments = group.segments.filter((s) => s.id !== segmentId);
    group.segments.forEach((s, i) => (s.order = i));
    recalcGroupTotals(group);
    group.updatedAt = Date.now();
    if (selectedSegmentId.value === segmentId) selectedSegmentId.value = null;
  }

  function selectSegment(id: string | null) {
    selectedSegmentId.value = id;
  }

  function reorderSegments(groupId: string, newOrderIds: string[]) {
    const group = taskGroups.value.find((g) => g.id === groupId);
    if (!group) return;
    const map = new Map(group.segments.map((s) => [s.id, s]));
    group.segments = newOrderIds
      .map((id) => map.get(id)!)
      .filter(Boolean);
    group.segments.forEach((s, i) => (s.order = i));
    group.updatedAt = Date.now();
  }

  function addTargetToSegment(
    groupId: string,
    segmentId: string,
    target: Omit<InspectionTarget, 'id'>
  ) {
    const group = taskGroups.value.find((g) => g.id === groupId);
    if (!group) return;
    const segment = group.segments.find((s) => s.id === segmentId);
    if (!segment) return;

    const newTarget: InspectionTarget = { id: genId('tgt'), ...target };
    segment.targets.push(newTarget);
    segment.waypoints = generateSegmentWaypoints(segment.targets);
    segment.stats = calculateSegmentStats(segment.targets);
    recalcGroupTotals(group);
    group.updatedAt = Date.now();
  }

  function removeTargetFromSegment(groupId: string, segmentId: string, targetId: string) {
    const group = taskGroups.value.find((g) => g.id === groupId);
    if (!group) return;
    const segment = group.segments.find((s) => s.id === segmentId);
    if (!segment) return;

    segment.targets = segment.targets.filter((t) => t.id !== targetId);
    segment.waypoints = generateSegmentWaypoints(segment.targets);
    segment.stats = calculateSegmentStats(segment.targets);
    recalcGroupTotals(group);
    group.updatedAt = Date.now();
  }

  function startSegment(groupId: string, segmentId: string) {
    const group = taskGroups.value.find((g) => g.id === groupId);
    if (!group) return;
    const segment = group.segments.find((s) => s.id === segmentId);
    if (!segment) return;

    segment.status = 'running';
    segment.startedAt = Date.now();
    group.currentSegmentIndex = segment.order;
    group.overallStatus = 'running';
    group.updatedAt = Date.now();
  }

  function updateSegmentProgress(
    groupId: string,
    segmentId: string,
    progress: number,
    result?: InspectionResult
  ) {
    const group = taskGroups.value.find((g) => g.id === groupId);
    if (!group) return;
    const segment = group.segments.find((s) => s.id === segmentId);
    if (!segment) return;

    segment.progress = Math.min(100, progress);
    if (result) {
      segment.results.push(result);
      if (result.anomalyDetected) group.anomalyCount++;
      const uniqueTargets = new Set(segment.results.map((r) => r.targetId));
      segment.stats.targetsCompleted = uniqueTargets.size;
    }
    const ratio = segment.progress / 100;
    segment.stats.completedDistance = segment.stats.totalDistance * ratio;
    segment.stats.completedTime = segment.stats.estimatedTime * ratio;
    segment.stats.usedBattery = segment.stats.batteryUsage * ratio;

    if (segment.progress >= 100) {
      segment.status = 'completed';
      segment.completedAt = Date.now();
    }
    recalcGroupTotals(group);
    recalcOverallProgress(group);
    updateOverallStatus(group);
    group.updatedAt = Date.now();
  }

  function pauseSegment(groupId: string, segmentId: string) {
    const group = taskGroups.value.find((g) => g.id === groupId);
    if (!group) return;
    const segment = group.segments.find((s) => s.id === segmentId);
    if (!segment) return;
    segment.status = 'paused';
    group.overallStatus = 'paused';
    group.updatedAt = Date.now();
  }

  function failSegment(groupId: string, segmentId: string) {
    const group = taskGroups.value.find((g) => g.id === groupId);
    if (!group) return;
    const segment = group.segments.find((s) => s.id === segmentId);
    if (!segment) return;
    segment.status = 'failed';
    segment.completedAt = Date.now();
    group.overallStatus = 'failed';
    group.updatedAt = Date.now();
  }

  function completeTarget(
    groupId: string,
    segmentId: string,
    targetId: string,
    opts: Partial<InspectionResult> = {}
  ) {
    const group = taskGroups.value.find((g) => g.id === groupId);
    if (!group) return;
    const segment = group.segments.find((s) => s.id === segmentId);
    if (!segment) return;
    const target = segment.targets.find((t) => t.id === targetId);
    if (!target) return;

    const result: InspectionResult = {
      targetId,
      completedAt: Date.now(),
      status: opts.status ?? 'success',
      photos: opts.photos,
      videoDuration: opts.videoDuration,
      notes: opts.notes,
      anomalyDetected: opts.anomalyDetected ?? false,
    };
    segment.results.push(result);
    if (result.anomalyDetected) group.anomalyCount++;

    const completedTargets = new Set(segment.results.map((r) => r.targetId));
    segment.stats.targetsCompleted = completedTargets.size;
    const pct = segment.stats.targetsTotal > 0
      ? (segment.stats.targetsCompleted / segment.stats.targetsTotal) * 100
      : 0;
    if (pct > segment.progress) {
      updateSegmentProgress(groupId, segmentId, pct);
    } else {
      recalcGroupTotals(group);
      group.updatedAt = Date.now();
    }
  }

  let segmentSimInterval: ReturnType<typeof setInterval> | null = null;

  function simulateSegmentFlight(groupId: string, segmentId: string) {
    const group = taskGroups.value.find((g) => g.id === groupId);
    if (!group) return;
    const segment = group.segments.find((s) => s.id === segmentId);
    if (!segment || segment.status === 'running' || isSimulating.value) return;

    startSegment(groupId, segmentId);
    isSimulating.value = true;
    simProgress.value = segment.progress;

    segmentSimInterval = setInterval(() => {
      const g = taskGroups.value.find((x) => x.id === groupId);
      const seg = g?.segments.find((x) => x.id === segmentId);
      if (!g || !seg) {
        if (segmentSimInterval) clearInterval(segmentSimInterval);
        isSimulating.value = false;
        return;
      }

      seg.progress += 1;
      simProgress.value = seg.progress;
      const ratio = seg.progress / 100;
      seg.stats.completedDistance = seg.stats.totalDistance * ratio;
      seg.stats.completedTime = seg.stats.estimatedTime * ratio;
      seg.stats.usedBattery = seg.stats.batteryUsage * ratio;

      const targetCount = seg.targets.length;
      if (targetCount > 0) {
        const expectedCompleted = Math.floor((seg.progress / 100) * targetCount);
        while (seg.stats.targetsCompleted < expectedCompleted) {
          const nextTargetIdx = seg.stats.targetsCompleted;
          const nextTarget = seg.targets[nextTargetIdx];
          if (nextTarget) {
            const r: InspectionResult = {
              targetId: nextTarget.id,
              completedAt: Date.now(),
              status: Math.random() > 0.9 ? 'warning' : 'success',
              photos: nextTarget.action === 'photo' ? Math.floor(Math.random() * 5) + 1 : undefined,
              videoDuration:
                nextTarget.action === 'video' ? Math.floor(Math.random() * 60) + 10 : undefined,
              anomalyDetected: Math.random() > 0.95,
            };
            seg.results.push(r);
            if (r.anomalyDetected) g.anomalyCount++;
            seg.stats.targetsCompleted++;
          } else {
            break;
          }
        }
      }

      if (seg.progress >= 100) {
        seg.progress = 100;
        simProgress.value = 100;
        seg.status = 'completed';
        seg.completedAt = Date.now();
        isSimulating.value = false;
        if (segmentSimInterval) clearInterval(segmentSimInterval);
        segmentSimInterval = null;
      }
      recalcGroupTotals(g);
      recalcOverallProgress(g);
      updateOverallStatus(g);
      g.updatedAt = Date.now();
    }, 50);
  }

  function recalcGroupTotals(group: TaskGroup) {
    const ts = group.totalStats;
    ts.totalDistance = 0;
    ts.totalEstimatedTime = 0;
    ts.totalBatteryUsage = 0;
    ts.completedDistance = 0;
    ts.completedTime = 0;
    ts.usedBattery = 0;
    ts.targetsTotal = 0;
    ts.targetsCompleted = 0;
    ts.segmentsTotal = group.segments.length;
    ts.segmentsCompleted = 0;

    for (const seg of group.segments) {
      const ss = seg.stats;
      ts.totalDistance += ss.totalDistance;
      ts.totalEstimatedTime += ss.estimatedTime;
      ts.totalBatteryUsage += ss.batteryUsage;
      ts.completedDistance += ss.completedDistance;
      ts.completedTime += ss.completedTime;
      ts.usedBattery += ss.usedBattery;
      ts.targetsTotal += ss.targetsTotal;
      ts.targetsCompleted += ss.targetsCompleted;
      if (seg.status === 'completed') ts.segmentsCompleted++;
    }
  }

  function recalcOverallProgress(group: TaskGroup) {
    if (group.segments.length === 0) {
      group.overallProgress = 0;
      return;
    }
    const total = group.segments.reduce((sum, s) => sum + s.progress, 0);
    group.overallProgress = total / group.segments.length;
  }

  function updateOverallStatus(group: TaskGroup) {
    if (group.segments.length === 0) {
      group.overallStatus = 'pending';
      return;
    }
    const allCompleted = group.segments.every((s) => s.status === 'completed');
    if (allCompleted) {
      group.overallStatus = 'completed';
      return;
    }
    const anyFailed = group.segments.some((s) => s.status === 'failed');
    if (anyFailed) {
      group.overallStatus = 'failed';
      return;
    }
    const anyRunning = group.segments.some((s) => s.status === 'running');
    if (anyRunning) {
      group.overallStatus = 'running';
      return;
    }
    const anyPaused = group.segments.some((s) => s.status === 'paused');
    if (anyPaused) {
      group.overallStatus = 'paused';
      return;
    }
    group.overallStatus = 'pending';
  }

  function createMockTaskGroup(): TaskGroup {
    const group = createTaskGroup('区域巡检编组-演示', '包含A/B/C三个区域的顺序巡检任务');
    const areas = [
      { name: 'A区-电力塔巡检', desc: '北部电力线路杆塔群巡检', baseLat: 39.92, baseLng: 116.41 },
      { name: 'B区-管道巡检', desc: '东部输油管道分段巡检', baseLat: 39.90, baseLng: 116.43 },
      { name: 'C区-厂区巡检', desc: '南部化工厂区安全巡检', baseLat: 39.88, baseLng: 116.39 },
    ];
    const actions: InspectionTarget['action'][] = ['hover', 'photo', 'video', 'photo'];
    const priorities: InspectionTarget['priority'][] = ['low', 'medium', 'high'];
    areas.forEach((area, ai) => {
      const targets: InspectionTarget[] = [];
      for (let i = 0; i < 4; i++) {
        targets.push({
          id: genId('tgt'),
          name: `${area.name.split('-')[0]}-目标${i + 1}`,
          description: `编号T${ai * 4 + i + 1}的巡检作业点`,
          lat: area.baseLat + (Math.random() - 0.5) * 0.015,
          lng: area.baseLng + (Math.random() - 0.5) * 0.015,
          altitude: 80 + Math.floor(Math.random() * 60),
          action: actions[Math.floor(Math.random() * actions.length)],
          hoverDuration: 20 + Math.floor(Math.random() * 60),
          priority: priorities[Math.floor(Math.random() * priorities.length)],
          tags: ['自动生成', `区域${area.name.split('-')[0]}`],
        });
      }
      addSegment(group.id, area.name, area.desc, targets);
    });
    return group;
  }

  // ─── Basic Actions (Legacy) ───────────────────────────────────────────────
  function addWaypoint(
    lat: number,
    lng: number,
    altitude = 100,
    speed = 10,
    action: Waypoint['action'] = 'none'
  ) {
    const id = genId('wp');
    waypoints.value.push({ id, lat, lng, altitude, speed, action });
  }

  function removeWaypoint(id: string) {
    waypoints.value = waypoints.value.filter((w) => w.id !== id);
  }

  function updateWaypoint(id: string, updates: Partial<Waypoint>) {
    const wp = waypoints.value.find((w) => w.id === id);
    if (wp) Object.assign(wp, updates);
  }

  function planRoute(start: [number, number], goal: [number, number]) {
    const bounds = { minLat: 39.85, maxLat: 39.95, minLng: 116.35, maxLng: 116.45 };
    let raw: Waypoint[];
    if (selectedAlgorithm.value === 'astar') {
      raw = aStarPathfind(start, goal, 30, noFlyZones.value, bounds);
    } else {
      raw = rrtPathfind(start, goal, noFlyZones.value);
    }
    const smoothed = smoothPath(raw);
    waypoints.value = smoothed;
    updatePlan();
  }

  function clearRoute() {
    waypoints.value = [];
    currentPlan.value = null;
    simProgress.value = 0;
  }

  function updatePlan() {
    const stats = calculateFlightStats(waypoints.value, droneConfig.value);
    currentPlan.value = {
      id: genId('plan'),
      name: 'Flight Plan',
      waypoints: [...waypoints.value],
      totalDistance: stats.totalDistance,
      estimatedTime: stats.estimatedTime,
      batteryUsage: stats.batteryUsage,
    };
  }

  let simInterval: ReturnType<typeof setInterval> | null = null;

  function simulateFlight() {
    if (waypoints.value.length < 2 || isSimulating.value) return;
    isSimulating.value = true;
    simProgress.value = 0;
    simInterval = setInterval(() => {
      simProgress.value += 1;
      if (simProgress.value >= 100) {
        simProgress.value = 100;
        isSimulating.value = false;
        if (simInterval) clearInterval(simInterval);
      }
    }, 50);
  }

  function loadMockData() {
    noFlyZones.value = mockNoFlyZones;
    terrainData.value = mockTerrainData;
  }

  function exportPlan(): string {
    if (!currentPlan.value) return '';
    return exportKML(currentPlan.value);
  }

  const totalDistance = computed(() => {
    if (!currentPlan.value) return 0;
    return currentPlan.value.totalDistance;
  });

  const estimatedTime = computed(() => {
    if (!currentPlan.value) return 0;
    return currentPlan.value.estimatedTime;
  });

  const batteryPercent = computed(() => {
    if (!currentPlan.value) return 0;
    return currentPlan.value.batteryUsage;
  });

  const terrainProfile = computed(() => {
    if (waypoints.value.length < 2) return [];
    return waypoints.value.map((wp) => {
      let nearestElev = 0;
      let minDist = Infinity;
      for (const tp of terrainData.value) {
        const d = (tp.lat - wp.lat) ** 2 + (tp.lng - wp.lng) ** 2;
        if (d < minDist) {
          minDist = d;
          nearestElev = tp.elevation;
        }
      }
      return {
        lat: wp.lat,
        lng: wp.lng,
        altitude: wp.altitude,
        terrainElevation: nearestElev,
      };
    });
  });

  return {
    waypoints,
    noFlyZones,
    terrainData,
    currentPlan,
    droneConfig,
    selectedAlgorithm,
    isSimulating,
    simProgress,
    mapCenter,
    totalDistance,
    estimatedTime,
    batteryPercent,
    terrainProfile,
    taskGroups,
    currentTaskGroupId,
    currentTaskGroup,
    selectedSegmentId,
    selectedSegment,
    addWaypoint,
    removeWaypoint,
    updateWaypoint,
    planRoute,
    clearRoute,
    simulateFlight,
    loadMockData,
    exportPlan,
    updatePlan,
    createTaskGroup,
    selectTaskGroup,
    deleteTaskGroup,
    addSegment,
    removeSegment,
    selectSegment,
    reorderSegments,
    addTargetToSegment,
    removeTargetFromSegment,
    startSegment,
    updateSegmentProgress,
    pauseSegment,
    failSegment,
    completeTarget,
    simulateSegmentFlight,
    createMockTaskGroup,
  };
});

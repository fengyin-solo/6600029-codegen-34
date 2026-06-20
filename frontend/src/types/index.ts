export interface Waypoint {
  id: string;
  lat: number;
  lng: number;
  altitude: number;   // meters AGL
  speed: number;      // m/s
  action: 'hover' | 'photo' | 'video' | 'none';
}

export interface FlightPlan {
  id: string;
  name: string;
  waypoints: Waypoint[];
  totalDistance: number;
  estimatedTime: number;
  batteryUsage: number;  // percentage
}

export interface NoFlyZone {
  id: string;
  name: string;
  center: [number, number];
  radius: number;  // meters
  type: 'airport' | 'military' | 'restricted';
}

export interface TerrainPoint {
  lat: number;
  lng: number;
  elevation: number;
}

export interface DroneConfig {
  maxAltitude: number;
  maxSpeed: number;
  batteryCapacity: number;  // mAh
  consumptionRate: number;  // mAh/min
  safeDistance: number;     // meters from obstacles
}

export type SegmentStatus = 'pending' | 'running' | 'completed' | 'paused' | 'failed';

export interface InspectionTarget {
  id: string;
  name: string;
  description?: string;
  lat: number;
  lng: number;
  altitude: number;
  action: 'hover' | 'photo' | 'video' | 'none';
  hoverDuration?: number;
  priority: 'low' | 'medium' | 'high';
  tags?: string[];
}

export interface InspectionResult {
  targetId: string;
  completedAt: number;
  status: 'success' | 'warning' | 'error';
  photos?: number;
  videoDuration?: number;
  notes?: string;
  anomalyDetected?: boolean;
}

export interface TaskSegment {
  id: string;
  name: string;
  description?: string;
  order: number;
  targets: InspectionTarget[];
  waypoints: Waypoint[];
  status: SegmentStatus;
  progress: number;
  stats: SegmentStats;
  results: InspectionResult[];
  startedAt?: number;
  completedAt?: number;
}

export interface SegmentStats {
  totalDistance: number;
  estimatedTime: number;
  batteryUsage: number;
  completedDistance: number;
  completedTime: number;
  usedBattery: number;
  targetsTotal: number;
  targetsCompleted: number;
}

export interface TaskGroup {
  id: string;
  name: string;
  description?: string;
  createdAt: number;
  updatedAt: number;
  segments: TaskSegment[];
  currentSegmentIndex: number;
  overallProgress: number;
  overallStatus: SegmentStatus;
  totalStats: {
    totalDistance: number;
    totalEstimatedTime: number;
    totalBatteryUsage: number;
    completedDistance: number;
    completedTime: number;
    usedBattery: number;
    targetsTotal: number;
    targetsCompleted: number;
    segmentsTotal: number;
    segmentsCompleted: number;
  };
  anomalyCount: number;
}

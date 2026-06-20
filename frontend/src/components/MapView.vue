<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch, nextTick, computed } from 'vue';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { useDroneStore } from '../store/drone';
import type { TaskSegment } from '../types';

const props = defineProps<{
  mode?: 'route' | 'taskgroup';
}>();

const store = useDroneStore();
const mapContainer = ref<HTMLElement>();
let map: L.Map | null = null;
let waypointLayer: L.LayerGroup | null = null;
let routeLayer: L.Polyline | null = null;
let zoneLayer: L.LayerGroup | null = null;
let segmentLayer: L.LayerGroup | null = null;
let droneMarker: L.CircleMarker | null = null;

const addMode = ref(false);

const segmentColors = ['#3b82f6', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899', '#06b6d4'];

function getSegmentColor(order: number): string {
  return segmentColors[order % segmentColors.length];
}

function initMap() {
  if (!mapContainer.value || map) return;
  map = L.map(mapContainer.value).setView(store.mapCenter, 12);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '© OpenStreetMap',
    maxZoom: 18,
  }).addTo(map);

  waypointLayer = L.layerGroup().addTo(map);
  zoneLayer = L.layerGroup().addTo(map);
  segmentLayer = L.layerGroup().addTo(map);

  map.on('click', (e: L.LeafletMouseEvent) => {
    if (addMode.value && props.mode === 'route') {
      store.addWaypoint(e.latlng.lat, e.latlng.lng);
    }
  });
}

function drawNoFlyZones() {
  if (!zoneLayer) return;
  zoneLayer.clearLayers();
  for (const zone of store.noFlyZones) {
    const color =
      zone.type === 'airport' ? '#ef4444' :
      zone.type === 'military' ? '#f97316' : '#a855f7';
    L.circle([zone.center[0], zone.center[1]], {
      radius: zone.radius,
      color,
      fillColor: color,
      fillOpacity: 0.15,
      weight: 2,
    })
      .bindPopup(`<b>${zone.name}</b><br>Type: ${zone.type}<br>Radius: ${zone.radius}m`)
      .addTo(zoneLayer);
  }
}

function drawWaypoints() {
  if (!waypointLayer) return;
  waypointLayer.clearLayers();
  store.waypoints.forEach((wp, idx) => {
    const marker = L.circleMarker([wp.lat, wp.lng], {
      radius: 8,
      color: '#3b82f6',
      fillColor: '#60a5fa',
      fillOpacity: 0.9,
      weight: 2,
    });
    marker.bindTooltip(`WP${idx + 1}`, { permanent: true, direction: 'top', className: 'wp-tooltip' });
    marker.bindPopup(`
      <div style="min-width:160px">
        <b>Waypoint ${idx + 1}</b><br>
        Altitude: ${wp.altitude}m<br>
        Speed: ${wp.speed} m/s<br>
        Action: ${wp.action}<br>
        <button onclick="this.closest('.leaflet-popup').remove()" style="margin-top:4px;color:#ef4444">Remove</button>
      </div>
    `);
    marker.on('dragend', (e: any) => {
      const ll = e.target.getLatLng();
      store.updateWaypoint(wp.id, { lat: ll.lat, lng: ll.lng });
    });
    marker.addTo(waypointLayer!);
  });
}

function drawRoute() {
  if (routeLayer && map) {
    map.removeLayer(routeLayer);
    routeLayer = null;
  }
  if (store.waypoints.length < 2 || !map) return;

  const latlngs = store.waypoints.map((w) => [w.lat, w.lng] as [number, number]);

  let hasDanger = false;
  for (const wp of store.waypoints) {
    for (const zone of store.noFlyZones) {
      const d = Math.sqrt(
        (wp.lat - zone.center[0]) ** 2 + (wp.lng - zone.center[1]) ** 2
      ) * 111000;
      if (d < zone.radius * 1.5) hasDanger = true;
    }
  }

  routeLayer = L.polyline(latlngs, {
    color: hasDanger ? '#ef4444' : '#22c55e',
    weight: 3,
    opacity: 0.8,
    dashArray: hasDanger ? '8,4' : undefined,
  }).addTo(map);
}

function drawSegments() {
  if (!segmentLayer || !map) return;
  segmentLayer.clearLayers();

  const group = store.currentTaskGroup;
  if (!group) return;

  group.segments.forEach((seg, segIdx) => {
    const color = getSegmentColor(seg.order);
    const isCurrent = store.currentTaskGroup?.currentSegmentIndex === seg.order && seg.status === 'running';
    const isSelected = store.selectedSegmentId === seg.id;

    if (seg.waypoints.length >= 2) {
      const latlngs = seg.waypoints.map((w) => [w.lat, w.lng] as [number, number]);
      L.polyline(latlngs, {
        color,
        weight: isSelected ? 5 : isCurrent ? 4 : 2.5,
        opacity: seg.status === 'completed' ? 0.5 : seg.status === 'pending' ? 0.6 : 0.9,
        dashArray: seg.status === 'pending' ? '6,6' : undefined,
      }).addTo(segmentLayer!);
    }

    seg.targets.forEach((t, tIdx) => {
      const completed = seg.results.some((r) => r.targetId === t.id);
      const anomaly = seg.results.find((r) => r.targetId === t.id)?.anomalyDetected;

      const icon = t.action === 'photo' ? '📷' : t.action === 'video' ? '🎬' : t.action === 'hover' ? '⏸' : '●';
      const markerColor = anomaly ? '#ef4444' : completed ? '#22c55e' : color;

      const marker = L.circleMarker([t.lat, t.lng], {
        radius: isSelected ? 11 : 9,
        color: markerColor,
        fillColor: markerColor,
        fillOpacity: completed ? 0.35 : 0.8,
        weight: isSelected ? 3 : 2,
      });

      marker.bindTooltip(
        `<div style="font-size:11px;font-weight:bold">${icon} ${t.name}</div>`,
        { permanent: true, direction: 'top', className: 'segment-wp-tooltip', offset: [0, -4] }
      );

      const resultHtml = (() => {
        const r = seg.results.find((x) => x.targetId === t.id);
        if (!r) return '';
        const statusColor = r.status === 'success' ? '#22c55e' : r.status === 'warning' ? '#f59e0b' : '#ef4444';
        const statusText = r.status === 'success' ? '成功' : r.status === 'warning' ? '警告' : '失败';
        return `
          <div style="margin-top:6px;padding-top:6px;border-top:1px solid #475569">
            <div style="color:${statusColor};font-weight:bold">${statusText}</div>
            ${r.photos ? `<div style="font-size:11px">📷 拍照: ${r.photos}张</div>` : ''}
            ${r.videoDuration ? `<div style="font-size:11px">🎬 录像: ${r.videoDuration}s</div>` : ''}
            ${r.anomalyDetected ? `<div style="font-size:11px;color:#ef4444">⚠ 检测到异常</div>` : ''}
          </div>
        `;
      })();

      marker.bindPopup(`
        <div style="min-width:180px;font-size:12px">
          <div style="display:flex;align-items:center;gap:6px;margin-bottom:4px">
            <span style="font-size:16px">${icon}</span>
            <b style="color:${markerColor}">${t.name}</b>
          </div>
          <div style="font-size:11px;color:#94a3b8;margin-bottom:4px">
            子任务 #${seg.order + 1} · ${seg.name} · 目标 ${tIdx + 1}/${seg.targets.length}
          </div>
          <div style="font-size:11px">
            <div>📍 ${t.lat.toFixed(5)}, ${t.lng.toFixed(5)}</div>
            <div>📏 高度: ${t.altitude}m</div>
            ${t.hoverDuration ? `<div>⏱ 悬停: ${t.hoverDuration}s</div>` : ''}
            <div style="display:inline-block;margin-top:2px;padding:1px 6px;border-radius:4px;font-size:10px;background:${
              t.priority === 'high' ? '#ef444433' : t.priority === 'medium' ? '#f59e0b33' : '#64748b33'
            };color:${
              t.priority === 'high' ? '#ef4444' : t.priority === 'medium' ? '#f59e0b' : '#94a3b8'
            }">
              ${t.priority === 'high' ? '高优先级' : t.priority === 'medium' ? '中优先级' : '低优先级'}
            </div>
          </div>
          ${t.description ? `<div style="font-size:11px;color:#94a3b8;margin-top:4px">${t.description}</div>` : ''}
          ${resultHtml}
        </div>
      `);

      marker.on('click', () => {
        store.selectSegment(seg.id);
      });

      marker.addTo(segmentLayer!);
    });

    const segLabel = L.marker(
      [seg.waypoints[0]?.lat ?? 0, seg.waypoints[0]?.lng ?? 0],
      {
        opacity: 0,
        icon: L.divIcon({
          className: '',
          html: `<div style="
            transform: translate(0, -28px);
            background: ${color}dd;
            color: white;
            padding: 2px 8px;
            border-radius: 4px;
            font-size: 10px;
            font-weight: bold;
            white-space: nowrap;
            pointer-events: none;
            box-shadow: 0 2px 4px rgba(0,0,0,0.3);
          ">${seg.order + 1}. ${seg.name}${seg.status === 'completed' ? ' ✓' : ''}</div>`,
          iconSize: [0, 0],
        }),
      }
    );
    if (seg.waypoints.length > 0) {
      segLabel.addTo(segmentLayer!);
    }
  });
}

function drawSimDrone() {
  if (!map) return;

  let wpList: { lat: number; lng: number }[] = [];
  let progress = 0;

  if (props.mode === 'taskgroup') {
    const group = store.currentTaskGroup;
    if (group && store.selectedSegment) {
      wpList = store.selectedSegment.waypoints;
      progress = store.selectedSegment.progress;
    } else {
      return;
    }
  } else {
    if (store.waypoints.length < 2) return;
    wpList = store.waypoints;
    progress = store.simProgress;
  }

  if (wpList.length < 2) return;

  const p = progress / 100;
  const totalWp = wpList.length;
  const segIdx = Math.min(Math.floor(p * (totalWp - 1)), totalWp - 2);
  const segProgress = p * (totalWp - 1) - segIdx;
  const wp1 = wpList[segIdx];
  const wp2 = wpList[segIdx + 1];
  const lat = wp1.lat + (wp2.lat - wp1.lat) * segProgress;
  const lng = wp1.lng + (wp2.lng - wp1.lng) * segProgress;

  if (droneMarker) {
    droneMarker.setLatLng([lat, lng]);
  } else {
    droneMarker = L.circleMarker([lat, lng], {
      radius: 10,
      color: '#fbbf24',
      fillColor: '#f59e0b',
      fillOpacity: 1,
      weight: 3,
    }).addTo(map);
  }
}

watch(() => store.waypoints.length, () => {
  drawWaypoints();
  drawRoute();
});

watch(() => store.noFlyZones.length, drawNoFlyZones);
watch(() => store.simProgress, drawSimDrone);

watch(
  () => [
    store.currentTaskGroup?.id,
    store.currentTaskGroup?.segments.length,
    store.selectedSegmentId,
    store.currentTaskGroup?.overallProgress,
    store.currentTaskGroup?.updatedAt,
  ],
  () => {
    drawSegments();
    drawSimDrone();
  },
  { deep: true }
);

watch(
  () => props.mode,
  () => {
    if (props.mode === 'taskgroup') {
      if (waypointLayer) waypointLayer.clearLayers();
      if (routeLayer && map) map.removeLayer(routeLayer);
      routeLayer = null;
      drawSegments();
    } else {
      if (segmentLayer) segmentLayer.clearLayers();
      drawWaypoints();
      drawRoute();
    }
  }
);

onMounted(() => {
  nextTick(() => {
    initMap();
    drawNoFlyZones();
    if (props.mode === 'taskgroup') {
      drawSegments();
    } else {
      drawWaypoints();
      drawRoute();
    }
  });
});

onUnmounted(() => {
  if (map) {
    map.remove();
    map = null;
  }
});

function toggleAddMode() {
  addMode.value = !addMode.value;
}

function handlePlanRoute() {
  if (store.waypoints.length < 2) return;
  const first = store.waypoints[0];
  const last = store.waypoints[store.waypoints.length - 1];
  store.planRoute([first.lat, first.lng], [last.lat, last.lng]);
}
</script>

<template>
  <div class="relative w-full h-full">
    <div ref="mapContainer" class="w-full h-full rounded-lg" />
    <div class="absolute top-2 right-2 z-[1000] flex flex-col gap-1">
      <template v-if="mode === 'route'">
        <button
          @click="toggleAddMode"
          :class="addMode ? 'bg-blue-600 text-white' : 'bg-gray-800 text-gray-300'"
          class="px-3 py-1 rounded text-xs font-medium shadow hover:opacity-90 transition"
        >
          {{ addMode ? '✦ 添加模式' : '○ 点击添加' }}
        </button>
        <button
          @click="handlePlanRoute"
          class="px-3 py-1 rounded text-xs font-medium bg-green-700 text-white shadow hover:opacity-90 transition"
        >
          规划航线
        </button>
        <button
          @click="store.clearRoute()"
          class="px-3 py-1 rounded text-xs font-medium bg-red-700 text-white shadow hover:opacity-90 transition"
        >
          清除
        </button>
      </template>
      <template v-else>
        <div class="bg-slate-800/95 rounded p-2 text-[10px] text-slate-300 space-y-0.5 shadow-lg backdrop-blur min-w-[160px]">
          <div class="font-semibold text-slate-200 border-b border-slate-700 pb-1 mb-1">图例</div>
          <div class="flex items-center gap-1.5">
            <div class="w-3 h-3 rounded-full" style="background:#22c55e"></div>
            <span>已完成目标</span>
          </div>
          <div class="flex items-center gap-1.5">
            <div class="w-3 h-3 rounded-full" style="background:#3b82f6"></div>
            <span>待执行目标</span>
          </div>
          <div class="flex items-center gap-1.5">
            <div class="w-3 h-3 rounded-full" style="background:#ef4444"></div>
            <span>异常目标</span>
          </div>
          <div class="flex items-center gap-1.5 mt-1 pt-1 border-t border-slate-700">
            <div class="w-5 h-0.5" style="background:#3b82f6"></div>
            <span>实线 = 执行中</span>
          </div>
          <div class="flex items-center gap-1.5">
            <div class="w-5 h-0.5 border-t-2 border-dashed" style="border-color:#3b82f6"></div>
            <span>虚线 = 待执行</span>
          </div>
          <div class="flex items-center gap-1.5">
            <div class="w-3 h-3 rounded-full" style="background:#fbbf24"></div>
            <span>无人机位置</span>
          </div>
        </div>
      </template>
    </div>

    <!-- Segment Legend -->
    <div
      v-if="mode === 'taskgroup' && store.currentTaskGroup && store.currentTaskGroup.segments.length > 0"
      class="absolute bottom-2 left-2 z-[1000] bg-slate-800/95 rounded p-2 shadow-lg backdrop-blur"
    >
      <div class="text-[10px] font-semibold text-slate-200 mb-1">子任务段</div>
      <div class="flex flex-col gap-0.5">
        <div
          v-for="seg in store.currentTaskGroup!.segments"
          :key="seg.id"
          class="flex items-center gap-1.5 text-[10px] cursor-pointer hover:bg-slate-700/50 px-1 rounded"
          @click="store.selectSegment(store.selectedSegmentId === seg.id ? null : seg.id)"
        >
          <div
            class="w-3 h-3 rounded-sm"
            :style="{ backgroundColor: getSegmentColor(seg.order) }"
          ></div>
          <span
            class="text-slate-300"
            :class="{ 'font-bold text-white': store.selectedSegmentId === seg.id }"
          >
            {{ seg.order + 1 }}. {{ seg.name }}
          </span>
          <span
            class="ml-auto px-1 rounded text-[9px]"
            :style="{
              color:
                seg.status === 'completed' ? '#22c55e' :
                seg.status === 'running' ? '#f59e0b' :
                seg.status === 'failed' ? '#ef4444' :
                seg.status === 'paused' ? '#3b82f6' : '#64748b'
            }"
          >
            {{ seg.progress.toFixed(0) }}%
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
:deep(.wp-tooltip) {
  background: rgba(30, 41, 59, 0.9);
  color: #e2e8f0;
  border: 1px solid #475569;
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 4px;
}
:deep(.segment-wp-tooltip) {
  background: rgba(15, 23, 42, 0.85);
  color: #e2e8f0;
  border: 1px solid #334155;
  font-size: 10px;
  padding: 2px 5px;
  border-radius: 4px;
  white-space: nowrap;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
}
</style>

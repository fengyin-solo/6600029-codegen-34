<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useDroneStore } from '../store/drone';
import type { InspectionTarget, SegmentStatus, TaskSegment } from '../types';

const store = useDroneStore();

const newGroupName = ref('');
const newGroupDesc = ref('');
const newSegmentName = ref('');
const newSegmentDesc = ref('');
const showTargetForm = ref<string | null>(null);

const newTarget = ref<{
  name: string;
  description: string;
  lat: number;
  lng: number;
  altitude: number;
  action: InspectionTarget['action'];
  hoverDuration: number;
  priority: InspectionTarget['priority'];
}>({
  name: '',
  description: '',
  lat: 39.9,
  lng: 116.4,
  altitude: 100,
  action: 'photo',
  hoverDuration: 30,
  priority: 'medium',
});

function formatTime(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  if (h > 0) return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

function statusLabel(s: SegmentStatus): string {
  return { pending: '待执行', running: '执行中', completed: '已完成', paused: '已暂停', failed: '失败' }[s];
}

function statusColor(s: SegmentStatus): string {
  return {
    pending: '#64748b',
    running: '#f59e0b',
    completed: '#22c55e',
    paused: '#3b82f6',
    failed: '#ef4444',
  }[s];
}

function priorityColor(p: InspectionTarget['priority']): string {
  return { low: '#64748b', medium: '#f59e0b', high: '#ef4444' }[p];
}

function actionIcon(a: InspectionTarget['action']): string {
  return { hover: '⏸', photo: '📷', video: '🎬', none: '•' }[a];
}

function handleCreateGroup() {
  if (!newGroupName.value.trim()) return;
  store.createTaskGroup(newGroupName.value.trim(), newGroupDesc.value.trim());
  newGroupName.value = '';
  newGroupDesc.value = '';
}

function handleAddSegment() {
  if (!store.currentTaskGroup || !newSegmentName.value.trim()) return;
  store.addSegment(store.currentTaskGroup.id, newSegmentName.value.trim(), newSegmentDesc.value.trim());
  newSegmentName.value = '';
  newSegmentDesc.value = '';
}

function handleAddTarget(segmentId: string) {
  if (!store.currentTaskGroup || !newTarget.value.name.trim()) return;
  store.addTargetToSegment(store.currentTaskGroup.id, segmentId, { ...newTarget.value });
  newTarget.value = {
    name: '',
    description: '',
    lat: 39.9,
    lng: 116.4,
    altitude: 100,
    action: 'photo',
    hoverDuration: 30,
    priority: 'medium',
  };
  showTargetForm.value = null;
}

function moveSegment(direction: 'up' | 'down', order: number) {
  if (!store.currentTaskGroup) return;
  const ids = store.currentTaskGroup.segments.map((s) => s.id);
  const target = direction === 'up' ? order - 1 : order + 1;
  if (target < 0 || target >= ids.length) return;
  [ids[order], ids[target]] = [ids[target], ids[order]];
  store.reorderSegments(store.currentTaskGroup.id, ids);
}

const overallStats = computed(() => {
  if (!store.currentTaskGroup) return null;
  const ts = store.currentTaskGroup.totalStats;
  return {
    ...ts,
    distPct: ts.totalDistance > 0 ? (ts.completedDistance / ts.totalDistance) * 100 : 0,
    timePct: ts.totalEstimatedTime > 0 ? (ts.completedTime / ts.totalEstimatedTime) * 100 : 0,
    battPct: ts.totalBatteryUsage > 0 ? (ts.usedBattery / ts.totalBatteryUsage) * 100 : 0,
    targetPct: ts.targetsTotal > 0 ? (ts.targetsCompleted / ts.targetsTotal) * 100 : 0,
    segPct: ts.segmentsTotal > 0 ? (ts.segmentsCompleted / ts.segmentsTotal) * 100 : 0,
  };
});

const targetResultMap = computed(() => {
  if (!store.selectedSegment) return new Map<string, any>();
  const map = new Map();
  for (const r of store.selectedSegment.results) {
    map.set(r.targetId, r);
  }
  return map;
});
</script>

<template>
  <div class="bg-slate-800 rounded-lg flex flex-col overflow-hidden" style="max-height: 100%">
    <!-- Header -->
    <div class="p-3 border-b border-slate-700">
      <h3 class="text-sm font-bold text-sky-400 flex items-center gap-2">
        🗂 多段任务编组
      </h3>
    </div>

    <div class="flex-1 overflow-y-auto">
      <!-- Task Group Selector / Creator -->
      <div class="p-3 space-y-2 border-b border-slate-700">
        <div v-if="store.taskGroups.length > 0" class="space-y-1">
          <label class="text-[10px] text-slate-400 uppercase">选择任务组</label>
          <select
            :value="store.currentTaskGroupId ?? ''"
            @change="(e: any) => store.selectTaskGroup(e.target.value || null)"
            class="w-full bg-slate-900 border border-slate-600 rounded px-2 py-1.5 text-xs text-slate-200"
          >
            <option value="">-- 未选择 --</option>
            <option v-for="g in store.taskGroups" :key="g.id" :value="g.id">
              {{ g.name }} ({{ g.segments.length }}段)
            </option>
          </select>
        </div>

        <div class="space-y-1">
          <label class="text-[10px] text-slate-400 uppercase">新建任务组</label>
          <input
            v-model="newGroupName"
            placeholder="名称（如：春季巡检任务）"
            class="w-full bg-slate-900 border border-slate-600 rounded px-2 py-1 text-xs text-slate-200 placeholder-slate-500"
          />
          <input
            v-model="newGroupDesc"
            placeholder="描述（可选）"
            class="w-full bg-slate-900 border border-slate-600 rounded px-2 py-1 text-xs text-slate-200 placeholder-slate-500"
          />
          <div class="flex gap-1">
            <button
              @click="handleCreateGroup"
              class="flex-1 py-1 text-[11px] rounded bg-sky-700 text-white hover:bg-sky-600 transition"
            >
              + 创建任务组
            </button>
            <button
              @click="store.createMockTaskGroup()"
              class="flex-1 py-1 text-[11px] rounded bg-purple-700 text-white hover:bg-purple-600 transition"
            >
              🎲 生成演示
            </button>
          </div>
        </div>
      </div>

      <!-- Current Group Overview -->
      <div v-if="store.currentTaskGroup" class="p-3 space-y-2 border-b border-slate-700 bg-slate-850">
        <div class="flex items-center justify-between">
          <div>
            <div class="text-sm font-semibold text-slate-100">{{ store.currentTaskGroup.name }}</div>
            <div v-if="store.currentTaskGroup.description" class="text-[10px] text-slate-500">
              {{ store.currentTaskGroup.description }}
            </div>
          </div>
          <span
            class="px-2 py-0.5 text-[10px] rounded-full font-semibold"
            :style="{ backgroundColor: statusColor(store.currentTaskGroup.overallStatus) + '33', color: statusColor(store.currentTaskGroup.overallStatus) }"
          >
            {{ statusLabel(store.currentTaskGroup.overallStatus) }}
          </span>
        </div>

        <div>
          <div class="flex justify-between text-[10px] text-slate-400 mb-1">
            <span>总体进度</span>
            <span>{{ store.currentTaskGroup.overallProgress.toFixed(1) }}%</span>
          </div>
          <div class="w-full bg-slate-700 rounded-full h-2">
            <div
              class="h-2 rounded-full transition-all bg-gradient-to-r from-sky-500 to-emerald-500"
              :style="{ width: store.currentTaskGroup.overallProgress + '%' }"
            />
          </div>
        </div>

        <div v-if="overallStats" class="grid grid-cols-2 gap-1 text-[10px]">
          <div class="bg-slate-900 rounded p-1.5">
            <div class="flex justify-between text-slate-500">
              <span>子任务</span>
              <span class="text-slate-200">{{ overallStats.segmentsCompleted }}/{{ overallStats.segmentsTotal }}</span>
            </div>
            <div class="w-full bg-slate-700 rounded-full h-1 mt-1">
              <div class="h-1 rounded-full bg-amber-500" :style="{ width: overallStats.segPct + '%' }" />
            </div>
          </div>
          <div class="bg-slate-900 rounded p-1.5">
            <div class="flex justify-between text-slate-500">
              <span>巡检目标</span>
              <span class="text-slate-200">{{ overallStats.targetsCompleted }}/{{ overallStats.targetsTotal }}</span>
            </div>
            <div class="w-full bg-slate-700 rounded-full h-1 mt-1">
              <div class="h-1 rounded-full bg-emerald-500" :style="{ width: overallStats.targetPct + '%' }" />
            </div>
          </div>
          <div class="bg-slate-900 rounded p-1.5">
            <div class="flex justify-between text-slate-500">
              <span>距离</span>
              <span class="text-slate-200">{{ (overallStats.completedDistance / 1000).toFixed(2) }}/{{ (overallStats.totalDistance / 1000).toFixed(2) }}km</span>
            </div>
            <div class="w-full bg-slate-700 rounded-full h-1 mt-1">
              <div class="h-1 rounded-full bg-sky-500" :style="{ width: overallStats.distPct + '%' }" />
            </div>
          </div>
          <div class="bg-slate-900 rounded p-1.5">
            <div class="flex justify-between text-slate-500">
              <span>异常</span>
              <span :class="store.currentTaskGroup.anomalyCount > 0 ? 'text-red-400' : 'text-slate-200'">
                {{ store.currentTaskGroup.anomalyCount }}
              </span>
            </div>
            <div class="w-full bg-slate-700 rounded-full h-1 mt-1">
              <div
                class="h-1 rounded-full"
                :class="store.currentTaskGroup.anomalyCount > 0 ? 'bg-red-500' : 'bg-slate-500'"
                :style="{ width: Math.min(100, store.currentTaskGroup.anomalyCount * 20) + '%' }"
              />
            </div>
          </div>
        </div>

        <div class="grid grid-cols-3 gap-1 text-[10px] text-slate-400">
          <div>预计: <span class="text-slate-200">{{ formatTime(overallStats?.totalEstimatedTime ?? 0) }}</span></div>
          <div>已用: <span class="text-slate-200">{{ formatTime(overallStats?.completedTime ?? 0) }}</span></div>
          <div>电量: <span class="text-slate-200">{{ (overallStats?.usedBattery ?? 0).toFixed(1) }}%</span></div>
        </div>

        <button
          @click="store.deleteTaskGroup(store.currentTaskGroup!.id)"
          class="w-full py-1 text-[10px] rounded bg-red-900/50 text-red-400 hover:bg-red-800/60 transition"
        >
          🗑 删除任务组
        </button>
      </div>

      <!-- Add Segment Form -->
      <div v-if="store.currentTaskGroup" class="p-3 space-y-1 border-b border-slate-700">
        <label class="text-[10px] text-slate-400 uppercase">添加子任务段</label>
        <input
          v-model="newSegmentName"
          placeholder="子任务名称（如：A区巡检）"
          class="w-full bg-slate-900 border border-slate-600 rounded px-2 py-1 text-xs text-slate-200 placeholder-slate-500"
        />
        <input
          v-model="newSegmentDesc"
          placeholder="描述（可选）"
          class="w-full bg-slate-900 border border-slate-600 rounded px-2 py-1 text-xs text-slate-200 placeholder-slate-500"
        />
        <button
          @click="handleAddSegment"
          class="w-full py-1 text-[11px] rounded bg-emerald-700 text-white hover:bg-emerald-600 transition"
        >
          + 添加子任务
        </button>
      </div>

      <!-- Segments List -->
      <div v-if="store.currentTaskGroup" class="p-2 space-y-2">
        <div class="text-[10px] text-slate-400 uppercase px-1">子任务列表（按顺序执行）</div>

        <div
          v-for="seg in store.currentTaskGroup.segments"
          :key="seg.id"
          class="bg-slate-900 rounded-lg border transition cursor-pointer"
          :class="store.selectedSegmentId === seg.id ? 'border-sky-500' : 'border-slate-700 hover:border-slate-600'"
          @click="store.selectSegment(store.selectedSegmentId === seg.id ? null : seg.id)"
        >
          <!-- Segment Header -->
          <div class="p-2 flex items-start gap-2">
            <div class="flex-shrink-0 w-6 h-6 rounded-full bg-slate-800 flex items-center justify-center text-[10px] font-bold text-slate-300">
              {{ seg.order + 1 }}
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2">
                <span class="text-xs font-semibold text-slate-100 truncate">{{ seg.name }}</span>
                <span
                  class="px-1.5 py-0.5 text-[9px] rounded-full font-semibold flex-shrink-0"
                  :style="{ backgroundColor: statusColor(seg.status) + '33', color: statusColor(seg.status) }"
                >
                  {{ statusLabel(seg.status) }}
                </span>
              </div>
              <div v-if="seg.description" class="text-[9px] text-slate-500 truncate">{{ seg.description }}</div>
              <div class="mt-1.5">
                <div class="flex justify-between text-[9px] text-slate-500 mb-0.5">
                  <span>进度</span>
                  <span>{{ seg.progress.toFixed(0) }}% · 目标{{ seg.stats.targetsCompleted }}/{{ seg.stats.targetsTotal }}</span>
                </div>
                <div class="w-full bg-slate-700 rounded-full h-1.5">
                  <div
                    class="h-1.5 rounded-full transition-all"
                    :style="{ width: seg.progress + '%', backgroundColor: statusColor(seg.status) }"
                  />
                </div>
              </div>
              <div class="mt-1 flex gap-2 text-[9px] text-slate-500">
                <span>📏 {{ (seg.stats.totalDistance / 1000).toFixed(2) }}km</span>
                <span>⏱ {{ formatTime(seg.stats.estimatedTime) }}</span>
                <span>🔋 {{ seg.stats.batteryUsage.toFixed(1) }}%</span>
              </div>
            </div>
            <div class="flex flex-col gap-0.5 flex-shrink-0">
              <button
                v-if="seg.status === 'pending' && seg.targets.length > 0"
                @click.stop="store.simulateSegmentFlight(store.currentTaskGroup!.id, seg.id)"
                class="px-2 py-0.5 text-[9px] rounded bg-amber-700 text-white hover:bg-amber-600"
                title="开始/模拟执行"
              >
                ▶
              </button>
              <button
                v-if="seg.status === 'running'"
                @click.stop="store.pauseSegment(store.currentTaskGroup!.id, seg.id)"
                class="px-2 py-0.5 text-[9px] rounded bg-sky-700 text-white hover:bg-sky-600"
                title="暂停"
              >
                ⏸
              </button>
              <div class="flex gap-0.5">
                <button
                  @click.stop="moveSegment('up', seg.order)"
                  :disabled="seg.order === 0"
                  class="w-5 h-4 text-[9px] rounded bg-slate-700 text-slate-300 hover:bg-slate-600 disabled:opacity-30"
                >↑</button>
                <button
                  @click.stop="moveSegment('down', seg.order)"
                  :disabled="seg.order === store.currentTaskGroup!.segments.length - 1"
                  class="w-5 h-4 text-[9px] rounded bg-slate-700 text-slate-300 hover:bg-slate-600 disabled:opacity-30"
                >↓</button>
              </div>
              <button
                @click.stop="store.removeSegment(store.currentTaskGroup!.id, seg.id)"
                class="px-2 py-0.5 text-[9px] rounded bg-red-900/50 text-red-400 hover:bg-red-800/60"
                title="删除"
              >
                ✕
              </button>
            </div>
          </div>

          <!-- Expanded: Targets List -->
          <div v-if="store.selectedSegmentId === seg.id" class="border-t border-slate-700 p-2 space-y-2">
            <div class="space-y-1">
              <div
                v-for="t in seg.targets"
                :key="t.id"
                class="bg-slate-800 rounded p-1.5 text-[10px]"
              >
                <div class="flex items-center justify-between gap-1">
                  <div class="flex items-center gap-1.5 min-w-0">
                    <span>{{ actionIcon(t.action) }}</span>
                    <span class="text-slate-200 font-medium truncate">{{ t.name }}</span>
                    <span
                      class="px-1 text-[8px] rounded"
                      :style="{ backgroundColor: priorityColor(t.priority) + '33', color: priorityColor(t.priority) }"
                    >{{ t.priority }}</span>
                  </div>
                  <div v-if="targetResultMap.get(t.id)" class="flex items-center gap-1 flex-shrink-0">
                    <span
                      v-if="targetResultMap.get(t.id).anomalyDetected"
                      class="text-red-400"
                      title="检测到异常"
                    >⚠</span>
                    <span
                      :class="{
                        'text-emerald-400': targetResultMap.get(t.id).status === 'success',
                        'text-amber-400': targetResultMap.get(t.id).status === 'warning',
                        'text-red-400': targetResultMap.get(t.id).status === 'error',
                      }"
                    >✓</span>
                  </div>
                  <button
                    v-else
                    @click.stop="store.removeTargetFromSegment(store.currentTaskGroup!.id, seg.id, t.id)"
                    class="text-red-400 opacity-60 hover:opacity-100 flex-shrink-0"
                    title="删除目标"
                  >✕</button>
                </div>
                <div class="mt-0.5 flex gap-2 text-slate-500 text-[9px]">
                  <span>{{ t.lat.toFixed(4) }},{{ t.lng.toFixed(4) }}</span>
                  <span>{{ t.altitude }}m</span>
                  <span v-if="t.hoverDuration">悬停{{ t.hoverDuration }}s</span>
                </div>
              </div>

              <div v-if="seg.targets.length === 0" class="text-[10px] text-slate-500 italic text-center py-2">
                暂无巡检目标
              </div>
            </div>

            <!-- Add Target -->
            <div v-if="showTargetForm === seg.id" class="bg-slate-800 rounded p-2 space-y-1">
              <input v-model="newTarget.name" placeholder="目标名称" class="w-full bg-slate-900 border border-slate-600 rounded px-2 py-1 text-[10px] text-slate-200" />
              <div class="grid grid-cols-2 gap-1">
                <input v-model.number="newTarget.lat" type="number" step="0.0001" placeholder="纬度" class="bg-slate-900 border border-slate-600 rounded px-2 py-1 text-[10px] text-slate-200" />
                <input v-model.number="newTarget.lng" type="number" step="0.0001" placeholder="经度" class="bg-slate-900 border border-slate-600 rounded px-2 py-1 text-[10px] text-slate-200" />
                <input v-model.number="newTarget.altitude" type="number" placeholder="高度m" class="bg-slate-900 border border-slate-600 rounded px-2 py-1 text-[10px] text-slate-200" />
                <input v-model.number="newTarget.hoverDuration" type="number" placeholder="悬停s" class="bg-slate-900 border border-slate-600 rounded px-2 py-1 text-[10px] text-slate-200" />
                <select v-model="newTarget.action" class="bg-slate-900 border border-slate-600 rounded px-2 py-1 text-[10px] text-slate-200">
                  <option value="hover">⏸ 悬停</option>
                  <option value="photo">📷 拍照</option>
                  <option value="video">🎬 录像</option>
                  <option value="none">• 无</option>
                </select>
                <select v-model="newTarget.priority" class="bg-slate-900 border border-slate-600 rounded px-2 py-1 text-[10px] text-slate-200">
                  <option value="low">低优先级</option>
                  <option value="medium">中优先级</option>
                  <option value="high">高优先级</option>
                </select>
              </div>
              <div class="flex gap-1">
                <button
                  @click="handleAddTarget(seg.id)"
                  class="flex-1 py-1 text-[10px] rounded bg-sky-700 text-white hover:bg-sky-600"
                >确认添加</button>
                <button
                  @click="showTargetForm = null"
                  class="flex-1 py-1 text-[10px] rounded bg-slate-700 text-slate-300 hover:bg-slate-600"
                >取消</button>
              </div>
            </div>
            <button
              v-else
              @click="showTargetForm = seg.id"
              class="w-full py-1 text-[10px] rounded bg-slate-700 text-slate-300 hover:bg-slate-600 transition"
            >
              + 添加巡检目标
            </button>

            <!-- Results -->
            <div v-if="seg.results.length > 0" class="border-t border-slate-700 pt-1.5 space-y-1">
              <div class="text-[9px] text-slate-400 uppercase">巡检结果</div>
              <div class="max-h-24 overflow-y-auto space-y-0.5">
                <div
                  v-for="(r, i) in seg.results"
                  :key="i"
                  class="flex items-center gap-1 text-[9px] bg-slate-800 rounded px-1.5 py-0.5"
                >
                  <span
                    :class="{
                      'text-emerald-400': r.status === 'success',
                      'text-amber-400': r.status === 'warning',
                      'text-red-400': r.status === 'error',
                    }"
                  >{{ r.status === 'success' ? '✓' : r.status === 'warning' ? '!' : '✗' }}</span>
                  <span class="text-slate-300 flex-1 truncate">{{ seg.targets.find(t => t.id === r.targetId)?.name || r.targetId }}</span>
                  <span v-if="r.photos" class="text-slate-500">📷{{ r.photos }}</span>
                  <span v-if="r.videoDuration" class="text-slate-500">🎬{{ r.videoDuration }}s</span>
                  <span v-if="r.anomalyDetected" class="text-red-400">⚠异常</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="store.currentTaskGroup.segments.length === 0" class="text-[11px] text-slate-500 italic text-center py-4">
          暂无子任务，请先添加
        </div>
      </div>

      <div v-else class="p-6 text-center text-[11px] text-slate-500">
        请先选择或创建任务组
      </div>
    </div>
  </div>
</template>

<style scoped>
.bg-slate-850 {
  background-color: #1a2435;
}
</style>

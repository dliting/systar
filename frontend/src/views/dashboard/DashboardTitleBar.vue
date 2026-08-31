<template>
  <header class="title-bar">
    <div class="title-left">
      <div class="title-deco"></div>
      <h1 class="title-text">
        <span class="title-sys">SYSTAR</span>
        <span class="title-sub">智能监控运维系统</span>
      </h1>
    </div>
    <div class="title-center">
      <slot name="views"></slot>
      <span class="clock">{{ timeStr }}</span>
    </div>
    <div class="title-right">
      <template v-if="isFullscreen">
        <button class="icon-btn" @click="goBack" title="后退">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="15 18 9 12 15 6"></polyline>
          </svg>
        </button>
        <button class="icon-btn" @click="goForward" title="前进">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="9 18 15 12 9 6"></polyline>
          </svg>
        </button>
      </template>
      <span class="live-badge">
        <span class="live-dot"></span>
        LIVE
      </span>
      <button class="icon-btn" @click="toggleFullscreen" :title="isFullscreen ? '退出全屏' : '全屏'">
        <svg v-if="!isFullscreen" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="15 3 21 3 21 9"></polyline><polyline points="9 21 3 21 3 15"></polyline>
          <line x1="21" y1="3" x2="14" y2="10"></line><line x1="3" y1="21" x2="10" y2="14"></line>
        </svg>
        <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="4 14 10 14 10 20"></polyline><polyline points="20 10 14 10 14 4"></polyline>
          <line x1="14" y1="10" x2="21" y2="3"></line><line x1="3" y1="21" x2="10" y2="14"></line>
        </svg>
      </button>
      <button class="icon-btn" @click="$emit('refresh')" :class="{ spinning: loading }" title="刷新">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="23 4 23 10 17 10"></polyline>
          <polyline points="1 20 1 14 7 14"></polyline>
          <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path>
        </svg>
      </button>
    </div>
    <div class="title-border-glow"></div>
  </header>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'

defineProps({ loading: Boolean })
defineEmits(['refresh'])

const router = useRouter()
const timeStr = ref('')
const isFullscreen = ref(false)
let timer = null

function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen().catch(() => {})
  } else {
    document.exitFullscreen().catch(() => {})
  }
}

function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
}

function goBack() { router.back() }
function goForward() { router.forward() }

function updateClock() {
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  timeStr.value = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

onMounted(() => {
  updateClock()
  timer = setInterval(updateClock, 1000)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})
</script>

<style scoped>
.title-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
  position: relative;
  z-index: 2;
  background: linear-gradient(180deg, rgba(0, 240, 255, 0.04) 0%, transparent 100%);
  border-bottom: 1px solid rgba(0, 240, 255, 0.1);
}

.title-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.title-deco {
  width: 4px;
  height: 28px;
  background: linear-gradient(180deg, #00f0ff, #0080ff);
  border-radius: 2px;
  box-shadow: 0 0 12px rgba(0, 240, 255, 0.4);
}

.title-text {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 0;
}

.title-sys {
  font-family: 'Consolas', 'Courier New', monospace;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 6px;
  color: #e6f1ff;
  text-shadow: 0 0 20px rgba(0, 240, 255, 0.3);
}

.title-sub {
  font-size: 13px;
  color: #8892b0;
  letter-spacing: 2px;
}

.title-center {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
}

.clock {
  font-family: 'Consolas', 'Courier New', monospace;
  font-size: 16px;
  color: #00f0ff;
  letter-spacing: 2px;
  text-shadow: 0 0 8px rgba(0, 240, 255, 0.3);
}

.title-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.live-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: 'Consolas', monospace;
  font-size: 11px;
  color: #00e676;
  letter-spacing: 2px;
  padding: 3px 12px;
  border: 1px solid rgba(0, 230, 118, 0.3);
  border-radius: 12px;
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #00e676;
  animation: livePulse 2s ease-in-out infinite;
}

@keyframes livePulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 4px #00e676; }
  50% { opacity: 0.4; box-shadow: none; }
}

.icon-btn {
  background: transparent;
  border: 1px solid rgba(0, 240, 255, 0.25);
  border-radius: 6px;
  color: #00f0ff;
  cursor: pointer;
  padding: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.icon-btn:hover {
  border-color: rgba(0, 240, 255, 0.6);
  box-shadow: 0 0 12px rgba(0, 240, 255, 0.2);
}

.icon-btn.spinning svg {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.title-border-glow {
  position: absolute;
  bottom: -1px;
  left: 10%;
  right: 10%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(0, 240, 255, 0.4), transparent);
}
</style>

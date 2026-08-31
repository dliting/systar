<template>
  <div class="breadcrumb-bar" :class="'theme-' + theme" v-if="items.length > 0">
    <template v-for="(item, i) in items" :key="i">
      <span
        class="breadcrumb-seg"
        :class="{ clickable: i < items.length - 1 && item.to }"
        @click="item.to && i < items.length - 1 && navigate(item.to)"
      >
        {{ item.title }}
        <el-icon v-if="i < items.length - 1" class="breadcrumb-arrow"><ArrowRight /></el-icon>
      </span>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'

const props = defineProps({
  items: { type: Array, default: null },
  theme: { type: String, default: 'light', validator: v => ['light', 'dark'].includes(v) }
})

const route  = useRoute()
const router = useRouter()

const autoItems = computed(() => {
  return route.matched
    .filter(r => r.meta?.title)
    .map(r => ({ title: r.meta.title, to: r.path }))
})

const items = computed(() => props.items || autoItems.value)

function navigate(to) {
  router.push(to)
}
</script>

<style scoped>
.breadcrumb-bar { padding: 8px 0; font-size: 13px; display: flex; align-items: center; flex-wrap: wrap; }
.breadcrumb-bar.theme-light { color: #666; }
.breadcrumb-bar.theme-dark  { color: #8892b0; }
.breadcrumb-seg { display: inline-flex; align-items: center; }
.theme-light .breadcrumb-seg.clickable { cursor: pointer; color: #409eff; }
.theme-dark  .breadcrumb-seg.clickable { cursor: pointer; color: #64ffda; }
.breadcrumb-seg.clickable:hover { text-decoration: underline; }
.theme-light .breadcrumb-arrow { font-size: 12px; margin: 0 4px; color: #999; }
.theme-dark  .breadcrumb-arrow { font-size: 12px; margin: 0 4px; color: #495670; }
</style>

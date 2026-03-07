<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="sidebar-title">Vortex 交易</div>
      <el-menu
        :default-active="activeMenu"
        class="sidebar-menu"
        router
      >
        <el-menu-item index="/orderbook">
          <span>订单簿</span>
        </el-menu-item>
        <el-menu-item index="/trades">
          <span>成交分布</span>
        </el-menu-item>
        <el-menu-item index="/console">
          <span>下单撤单</span>
        </el-menu-item>
        <el-menu-item index="/analytics">
          <span>风控指标</span>
        </el-menu-item>
      </el-menu>

      <div class="filter-section">
        <div class="filter-title">全局筛选</div>
        <el-form label-position="top" size="small" class="filter-form">
          <el-form-item label="股东号">
            <el-input v-model.trim="filter.editShareholderId" placeholder="10位，如 A001000000" maxlength="10" show-word-limit />
          </el-form-item>
          <el-form-item label="股票代码">
            <el-input v-model.trim="filter.editSecurityId" placeholder="6位，如 600030" maxlength="6" show-word-limit />
          </el-form-item>
          <el-form-item label="深度">
            <el-input-number v-model="filter.depth" :min="1" :max="50" :step="1" style="width: 100%" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" plain size="small" class="switch-btn" @click="filter.applyEdit">
              切换
            </el-button>
          </el-form-item>
        </el-form>
      </div>

    </aside>
    <main class="main">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <keep-alive :max="10">
            <component :is="Component" :key="route.fullPath" />
          </keep-alive>
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { useFilterStore } from '../stores/filter';

const route = useRoute();
const filter = useFilterStore();

const activeMenu = computed(() => route.path || '/orderbook');
</script>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
  background: #f3f4f6;
}

.sidebar {
  width: 240px;
  min-width: 240px;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
}

.sidebar-title {
  padding: 16px;
  font-size: 16px;
  font-weight: 700;
  color: #111827;
  border-bottom: 1px solid #e5e7eb;
}

.sidebar-menu {
  border-right: none;
  flex: 0 0 auto;
}

.sidebar-menu .el-menu-item {
  height: 48px;
  line-height: 48px;
}

.filter-section {
  padding: 12px;
  border-top: 1px solid #e5e7eb;
  margin-top: auto;
}

.filter-title {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 8px;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 10px;
}

.filter-form :deep(.el-form-item__label) {
  font-size: 12px;
  color: #374151;
}

.switch-btn {
  width: 100%;
}

.main {
  flex: 1;
  overflow: auto;
  padding: 16px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>

<script setup lang="ts">
import { onMounted, ref } from 'vue'

import type { NotificationView, PageResponse } from '@/features/api/types'
import {
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/features/community/api'

const notifications = ref<PageResponse<NotificationView>>({
  items: [],
  page: 0,
  size: 10,
  total: 0,
})
const unreadOnly = ref(false)
const loading = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)

const typeLabels: Record<string, string> = {
  COMMENT_REPLY: '回复',
  MODERATION_RESULT: '治理结果',
  ANNOUNCEMENT: '公告',
  SYSTEM: '系统',
}

async function load(page = 0): Promise<void> {
  loading.value = true
  error.value = null
  try {
    notifications.value = await listNotifications({
      unreadOnly: unreadOnly.value,
      page,
      size: notifications.value.size,
    })
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '通知加载失败'
  } finally {
    loading.value = false
  }
}

function changeFilter(): void {
  void load(0)
}

function changePage(page: number): void {
  void load(page - 1)
}

async function markRead(notification: NotificationView): Promise<void> {
  if (notification.readAt) {
    return
  }
  try {
    await markNotificationRead(notification.notificationId)
    await load(notifications.value.page)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '标记已读失败'
  }
}

async function markAll(): Promise<void> {
  try {
    const result = await markAllNotificationsRead()
    notice.value = `已将 ${result.updated} 条通知标记为已读。`
    await load(0)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '全部已读失败'
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">个人中心</p>
        <h1>站内通知</h1>
        <p class="lead">评论回复、举报处理结果和系统公告都会保留在这里。</p>
      </div>
      <el-button :disabled="notifications.total === 0" @click="markAll">全部标记已读</el-button>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="notice" :title="notice" type="success" show-icon :closable="true" />

    <div class="filter-row">
      <el-radio-group v-model="unreadOnly" @change="changeFilter">
        <el-radio-button :value="false">全部</el-radio-button>
        <el-radio-button :value="true">未读</el-radio-button>
      </el-radio-group>
      <span>{{ notifications.total }} 条</span>
    </div>

    <div v-if="loading" class="state">正在加载通知...</div>

    <article
      v-for="notification in notifications.items"
      :key="notification.notificationId"
      class="notification-card"
      :class="{ unread: !notification.readAt }"
    >
      <div class="notification-main">
        <div class="meta">
          <el-tag size="small" type="info" effect="plain">
            {{ typeLabels[notification.type] ?? notification.type }}
          </el-tag>
          <span>{{ new Date(notification.createdAt).toLocaleString() }}</span>
          <el-tag v-if="!notification.readAt" size="small" type="warning">未读</el-tag>
        </div>
        <h2>{{ notification.title }}</h2>
        <p>{{ notification.body }}</p>
      </div>
      <div class="actions">
        <el-button v-if="!notification.readAt" text type="primary" @click="markRead(notification)">
          标记已读
        </el-button>
      </div>
    </article>

    <div v-if="!loading && notifications.items.length === 0" class="empty-state">
      {{ unreadOnly ? '没有未读通知。' : '还没有通知。' }}
    </div>

    <el-pagination
      v-if="notifications.total > notifications.size"
      class="pagination"
      layout="prev, pager, next"
      :current-page="notifications.page + 1"
      :page-size="notifications.size"
      :total="notifications.total"
      @current-change="changePage"
    />
  </section>
</template>

<style scoped>
.page {
  width: min(56rem, 100%);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.page-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.eyebrow {
  margin: 0 0 0.4rem;
  color: var(--color-accent);
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  font-size: clamp(1.4rem, 3vw, 1.9rem);
}

.lead {
  margin: 0.5rem 0 0;
  color: var(--color-muted);
}

.filter-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--color-muted);
  font-size: 0.85rem;
}

.notification-card {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 1rem 1.15rem;
  border: 1px solid var(--color-border);
  border-radius: 0.85rem;
  background: var(--color-surface);
}

.notification-card.unread {
  border-left: 4px solid var(--color-accent);
}

.notification-main {
  flex: 1 1 22rem;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 0.9rem;
  color: var(--color-muted);
  font-size: 0.78rem;
}

h2 {
  margin: 0.5rem 0 0.25rem;
  font-size: 1rem;
}

.notification-main p {
  margin: 0;
  color: var(--color-muted);
  line-height: 1.65;
}

.actions {
  display: flex;
  align-items: flex-start;
}

.state,
.empty-state {
  padding: 2rem;
  border: 1px dashed var(--color-border);
  border-radius: 0.8rem;
  color: var(--color-muted);
  text-align: center;
}

.pagination {
  margin-top: 0.5rem;
}
</style>

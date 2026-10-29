<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import type { AdminUserView, PageResponse, ResetPasswordResponse } from '@/features/api/types'
import { disableUser, enableUser, resetUserPassword, searchUsers } from '@/features/admin/api'
import { useSessionStore } from '@/features/auth/store'

const session = useSessionStore()
const users = ref<PageResponse<AdminUserView>>({ items: [], page: 0, size: 10, total: 0 })
const loading = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const filters = reactive({ keyword: '', status: '' })

const resetResult = ref<ResetPasswordResponse | null>(null)
const resetVisible = ref(false)
const actionUserId = ref<string | null>(null)

async function load(page = 0): Promise<void> {
  loading.value = true
  error.value = null
  try {
    users.value = await searchUsers({
      keyword: filters.keyword.trim() || undefined,
      status: filters.status || undefined,
      page,
      size: users.value.size,
    })
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '用户加载失败'
  } finally {
    loading.value = false
  }
}

function changePage(page: number): void {
  void load(page - 1)
}

async function toggleStatus(user: AdminUserView): Promise<void> {
  const disabling = user.status === 'ACTIVE'
  const confirmed = window.confirm(
    disabling
      ? `禁用 ${user.username} 后，其现有会话将立即失效。确定继续吗？`
      : `确定重新启用 ${user.username} 吗？`,
  )
  if (!confirmed) {
    return
  }
  actionUserId.value = user.userId
  error.value = null
  try {
    if (disabling) {
      await disableUser(user.userId)
    } else {
      await enableUser(user.userId)
    }
    notice.value = disabling ? '用户已禁用，会话已失效。' : '用户已启用。'
    await load(users.value.page)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '用户状态更新失败'
  } finally {
    actionUserId.value = null
  }
}

async function resetPassword(user: AdminUserView): Promise<void> {
  const confirmed = window.confirm(
    `重置 ${user.username} 的密码会使其现有会话失效，临时密码只显示一次。确定继续吗？`,
  )
  if (!confirmed) {
    return
  }
  actionUserId.value = user.userId
  error.value = null
  try {
    resetResult.value = await resetUserPassword(user.userId)
    resetVisible.value = true
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '密码重置失败'
  } finally {
    actionUserId.value = null
  }
}

async function copyTemporaryPassword(): Promise<void> {
  if (!resetResult.value) {
    return
  }
  try {
    await navigator.clipboard.writeText(resetResult.value.temporaryPassword)
    notice.value = '临时密码已复制到剪贴板。'
  } catch {
    notice.value = '无法自动复制，请手动选择临时密码。'
  }
}

function closeResetDialog(): void {
  resetVisible.value = false
  resetResult.value = null
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">运营管理</p>
        <h1>用户管理</h1>
        <p class="lead">禁用会立即失效该用户的现有会话；密码重置只显示一次。</p>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="notice" :title="notice" type="success" show-icon :closable="true" />

    <div class="filters">
      <el-input
        v-model="filters.keyword"
        placeholder="用户名或昵称"
        clearable
        @keyup.enter="load(0)"
      />
      <el-select v-model="filters.status" placeholder="状态" clearable @change="load(0)">
        <el-option label="正常" value="ACTIVE" />
        <el-option label="已禁用" value="DISABLED" />
        <el-option label="已注销" value="DELETED_ANONYMIZED" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="load(0)">搜索</el-button>
    </div>

    <p class="result-count">共 {{ users.total }} 个用户</p>

    <el-table v-loading="loading" :data="users.items" stripe>
      <el-table-column prop="username" label="用户名" min-width="140">
        <template #default="{ row }">
          <div class="user-cell">
            <strong>{{ row.username }}</strong>
            <el-tag
              v-if="row.userId === session.user?.userId"
              size="small"
              type="success"
              effect="plain"
            >
              当前账号
            </el-tag>
          </div>
          <span class="nickname">{{ row.nickname || '未设置昵称' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="角色" width="120">
        <template #default="{ row }">
          <el-tag
            v-for="role in row.roles"
            :key="role"
            size="small"
            :type="role === 'ADMIN' ? 'warning' : 'info'"
            effect="plain"
          >
            {{ role }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="
              row.status === 'ACTIVE' ? 'success' : row.status === 'DISABLED' ? 'danger' : 'info'
            "
          >
            {{ row.status === 'ACTIVE' ? '正常' : row.status === 'DISABLED' ? '已禁用' : '已注销' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" min-width="170">
        <template #default="{ row }">
          {{ new Date(row.createdAt).toLocaleString() }}
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="230">
        <template #default="{ row }">
          <el-button
            v-if="row.status !== 'DELETED_ANONYMIZED' && row.userId !== session.user?.userId"
            text
            :type="row.status === 'ACTIVE' ? 'danger' : 'success'"
            :loading="actionUserId === row.userId"
            @click="toggleStatus(row)"
          >
            {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
          </el-button>
          <el-button
            v-if="row.status !== 'DELETED_ANONYMIZED'"
            text
            type="primary"
            :loading="actionUserId === row.userId"
            @click="resetPassword(row)"
          >
            重置密码
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="users.total > users.size"
      class="pagination"
      layout="prev, pager, next"
      :current-page="users.page + 1"
      :page-size="users.size"
      :total="users.total"
      @current-change="changePage"
    />

    <el-dialog
      v-model="resetVisible"
      title="临时密码（仅显示一次）"
      width="min(30rem, 92vw)"
      @closed="closeResetDialog"
    >
      <div v-if="resetResult" class="reset-body">
        <p>
          用户 <strong>{{ resetResult.username }}</strong> 的密码已重置，请通过安全渠道转交。
          关闭后无法再次查看。
        </p>
        <el-input :model-value="resetResult.temporaryPassword" readonly />
        <el-button type="primary" @click="copyTemporaryPassword">复制临时密码</el-button>
      </div>
      <template #footer>
        <el-button @click="closeResetDialog">我已记录，关闭</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page {
  width: min(68rem, 100%);
  margin: 0 auto;
}

.page-head {
  margin-bottom: 1.25rem;
}

.eyebrow {
  margin: 0 0 0.4rem;
  color: #3157c8;
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

.filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
  gap: 0.75rem;
  margin: 1rem 0;
  padding: 1rem;
  border: 1px solid #dbe2ef;
  border-radius: 0.8rem;
  background: #ffffff;
}

.result-count {
  color: var(--color-muted);
  font-size: 0.9rem;
}

.user-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
}

.nickname {
  color: var(--color-muted);
  font-size: 0.8rem;
}

.reset-body {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.reset-body p {
  margin: 0;
  line-height: 1.6;
}

.pagination {
  margin-top: 1rem;
}
</style>

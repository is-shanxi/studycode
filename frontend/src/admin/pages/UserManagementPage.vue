<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import type { AdminUserView, PageResponse } from '@/features/api/types'
import { disableUser, enableUser, resetUserPassword, searchUsers } from '@/features/admin/api'
import { useSessionStore } from '@/features/auth/store'

const session = useSessionStore()
const users = ref<PageResponse<AdminUserView>>({ items: [], page: 0, size: 10, total: 0 })
const loading = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const filters = reactive({ keyword: '', status: '' })

const resetVisible = ref(false)
const resetTarget = ref<AdminUserView | null>(null)
const resetSubmitting = ref(false)
const resetError = ref<string | null>(null)
const resetForm = reactive({ newPassword: '', confirmation: '' })
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

function openResetDialog(user: AdminUserView): void {
  resetTarget.value = user
  resetError.value = null
  resetForm.newPassword = ''
  resetForm.confirmation = ''
  resetVisible.value = true
}

async function submitReset(): Promise<void> {
  if (!resetTarget.value) {
    return
  }
  if (resetForm.newPassword.length < 8) {
    resetError.value = '临时密码至少需要 8 个字符。'
    return
  }
  if (resetForm.newPassword !== resetForm.confirmation) {
    resetError.value = '两次输入的临时密码不一致。'
    return
  }
  resetSubmitting.value = true
  resetError.value = null
  try {
    await resetUserPassword(resetTarget.value.userId, {
      newPassword: resetForm.newPassword,
      newPasswordConfirmation: resetForm.confirmation,
    })
    notice.value = '密码已重置，现有会话已失效；请通过安全渠道转交临时密码。'
    closeResetDialog()
  } catch (caught) {
    resetError.value = caught instanceof Error ? caught.message : '密码重置失败'
  } finally {
    resetSubmitting.value = false
  }
}

function closeResetDialog(): void {
  resetVisible.value = false
  resetTarget.value = null
  resetError.value = null
  resetForm.newPassword = ''
  resetForm.confirmation = ''
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
        <p class="lead">
          禁用会立即失效该用户的现有会话；密码重置由管理员设置临时密码，响应不返回密码。
        </p>
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
            @click="openResetDialog(row)"
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
      title="重置密码"
      width="min(32rem, 92vw)"
      @closed="closeResetDialog"
    >
      <div v-if="resetTarget" class="reset-body">
        <p>
          为用户 <strong>{{ resetTarget.username }}</strong>
          设置临时密码。重置后其现有会话立即失效，请通过安全渠道转交。
        </p>
        <el-alert v-if="resetError" :title="resetError" type="error" show-icon :closable="false" />
        <el-input
          v-model="resetForm.newPassword"
          type="password"
          show-password
          maxlength="72"
          placeholder="新的临时密码（至少 8 个字符）"
        />
        <el-input
          v-model="resetForm.confirmation"
          type="password"
          show-password
          maxlength="72"
          placeholder="再次输入临时密码"
          @keyup.enter="submitReset"
        />
      </div>
      <template #footer>
        <el-button @click="closeResetDialog">取消</el-button>
        <el-button type="primary" :loading="resetSubmitting" @click="submitReset">
          确认重置
        </el-button>
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

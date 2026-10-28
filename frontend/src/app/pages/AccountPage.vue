<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import type { Profile } from '@/features/api/types'
import { useSessionStore } from '@/features/auth/store'
import { apiClient } from '@/features/http/client'

const session = useSessionStore()
const router = useRouter()
const profile = ref<Profile | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const success = ref<string | null>(null)
const savingProfile = ref(false)
const savingPassword = ref(false)
const deleting = ref(false)

const profileForm = reactive({ nickname: '' })
const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  newPasswordConfirmation: '',
})

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  try {
    profile.value = await apiClient.get<Profile>('/me/profile')
    profileForm.nickname = profile.value.nickname ?? ''
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '个人资料加载失败'
  } finally {
    loading.value = false
  }
}

async function saveProfile(): Promise<void> {
  savingProfile.value = true
  error.value = null
  success.value = null
  try {
    const updated = await apiClient.patch<Profile>('/me/profile', {
      nickname: profileForm.nickname,
    })
    profile.value = updated
    success.value = '昵称已更新'
    await session.fetchCurrentUser()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '保存失败'
  } finally {
    savingProfile.value = false
  }
}

async function changePassword(): Promise<void> {
  error.value = null
  success.value = null
  if (passwordForm.newPassword.length < 8) {
    error.value = '新密码至少需要 8 个字符'
    return
  }
  if (passwordForm.newPassword !== passwordForm.newPasswordConfirmation) {
    error.value = '两次输入的新密码不一致'
    return
  }
  savingPassword.value = true
  try {
    await apiClient.put<void>('/me/password', passwordForm)
    passwordForm.currentPassword = ''
    passwordForm.newPassword = ''
    passwordForm.newPasswordConfirmation = ''
    success.value = '密码已修改，其他会话已失效'
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '修改密码失败'
  } finally {
    savingPassword.value = false
  }
}

async function deleteAccount(): Promise<void> {
  if (!window.confirm('注销后账号将匿名化且无法登录，确定继续吗？')) {
    return
  }
  deleting.value = true
  error.value = null
  try {
    await apiClient.delete<void>('/me')
    await session.logout()
    await router.push({ name: 'app-login' })
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '注销失败'
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">个人中心</p>
        <h1>账号设置</h1>
        <p class="lead">管理员账号不能自行注销；普通用户注销后账号会匿名化保留业务关联。</p>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="success" :title="success" type="success" show-icon :closable="false" />

    <div v-if="loading" class="state">正在加载个人资料...</div>

    <template v-if="profile">
      <section class="card">
        <h2>账号信息</h2>
        <dl class="profile-grid">
          <div>
            <dt>用户名</dt>
            <dd>{{ profile.username }}</dd>
          </div>
          <div>
            <dt>状态</dt>
            <dd>{{ profile.status }}</dd>
          </div>
          <div>
            <dt>角色</dt>
            <dd>{{ profile.roles.join(' / ') }}</dd>
          </div>
        </dl>
      </section>

      <section class="card">
        <h2>修改昵称</h2>
        <el-form label-position="top" @submit.prevent="saveProfile">
          <el-form-item label="昵称">
            <el-input v-model="profileForm.nickname" placeholder="讨论区展示名称" maxlength="64" />
          </el-form-item>
          <el-button type="primary" :loading="savingProfile" @click="saveProfile">
            保存昵称
          </el-button>
        </el-form>
      </section>

      <section class="card">
        <h2>修改密码</h2>
        <el-form label-position="top" @submit.prevent="changePassword">
          <el-form-item label="当前密码">
            <el-input v-model="passwordForm.currentPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="passwordForm.newPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input
              v-model="passwordForm.newPasswordConfirmation"
              type="password"
              show-password
            />
          </el-form-item>
          <el-button type="primary" :loading="savingPassword" @click="changePassword">
            修改密码
          </el-button>
        </el-form>
      </section>

      <section class="card danger-card">
        <h2>注销账号</h2>
        <p class="danger-text">
          注销会停止登录并匿名化用户名和昵称，历史作答、自评和审计记录仍会保留。
        </p>
        <el-button type="danger" :loading="deleting" @click="deleteAccount">注销账号</el-button>
      </section>
    </template>
  </section>
</template>

<style scoped>
.page {
  width: min(48rem, 100%);
  margin: 0 auto;
}

.page-head {
  margin-bottom: 1.25rem;
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

h2 {
  margin: 0 0 0.9rem;
  font-size: 1.05rem;
}

.lead {
  margin: 0.5rem 0 0;
  color: var(--color-muted);
}

.card {
  margin-top: 1rem;
  padding: 1.25rem;
  border: 1px solid var(--color-border);
  border-radius: 0.8rem;
  background: var(--color-surface);
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
  gap: 1rem;
  margin: 0;
}

dt {
  color: var(--color-muted);
  font-size: 0.8rem;
}

dd {
  margin: 0.25rem 0 0;
  font-weight: 600;
}

.danger-card {
  border-color: #fecaca;
}

.danger-text {
  color: #991b1b;
  font-size: 0.9rem;
}

.state {
  padding: 2rem;
  color: var(--color-muted);
  text-align: center;
}
</style>

<script setup lang="ts">
import { NButton, NInput } from 'naive-ui'

import AppIcon from '@/components/AppIcon.vue'

/** 菜单表单中可编辑的权限资料。 */
interface EditablePermission {
  /** 后端鉴权代码 */
  code: string
  /** 中文名称 */
  name: string
  /** 用途说明 */
  description?: string
}

const props = defineProps<{
  /** 当前菜单声明的完整权限资料 */
  modelValue: EditablePermission[]
  /** 后端字段错误 */
  error?: string
}>()

const emit = defineEmits<{
  /** 更新完整权限资料 */
  (event: 'update:modelValue', value: EditablePermission[]): void
}>()

/** 新增一行空权限，等待用户同时填写代码与中文名称。 */
function addPermission(): void {
  emit('update:modelValue', [...props.modelValue, { code: '', name: '', description: '' }])
}

/**
 * 修改指定权限字段。
 *
 * @param index 行下标
 * @param field 字段名
 * @param value 新值
 */
function updatePermission(index: number, field: keyof EditablePermission, value: string): void {
  const next = props.modelValue.map((item, itemIndex) =>
    itemIndex === index ? { ...item, [field]: value } : item,
  )
  emit('update:modelValue', next)
}

/**
 * 删除指定权限行。
 *
 * @param index 行下标
 */
function removePermission(index: number): void {
  emit('update:modelValue', props.modelValue.filter((_item, itemIndex) => itemIndex !== index))
}

/** 清空临时状态；保留方法以兼容菜单弹窗的重置调用。 */
function reset(): void {
  // 当前组件没有独立草稿状态，数据完全由 v-model 驱动
}

defineExpose({ reset })
</script>

<template>
  <div class="permission-editor">
    <div
      v-if="modelValue.length > 0"
      class="permission-editor__head"
    >
      <span>权限标识</span>
      <span>中文名称</span>
      <span>用途说明</span>
      <span />
    </div>
    <div
      v-for="(permission, index) in modelValue"
      :key="index"
      class="permission-editor__row"
    >
      <NInput
        :value="permission.code"
        placeholder="file:storage:view"
        @update:value="updatePermission(index, 'code', $event)"
      />
      <NInput
        :value="permission.name"
        placeholder="查询存储配置"
        @update:value="updatePermission(index, 'name', $event)"
      />
      <NInput
        :value="permission.description"
        placeholder="说明该权限允许执行什么操作"
        @update:value="updatePermission(index, 'description', $event)"
      />
      <NButton
        quaternary
        circle
        type="error"
        attr-type="button"
        title="移除权限"
        @click="removePermission(index)"
      >
        <AppIcon
          name="close"
          :size="15"
        />
      </NButton>
    </div>

    <NButton
      class="permission-editor__add"
      dashed
      block
      attr-type="button"
      @click="addPermission"
    >
      <template #icon>
        <AppIcon
          name="plus"
          :size="16"
        />
      </template>
      添加接口权限
    </NButton>

    <p
      v-if="error"
      class="permission-editor__error"
    >
      {{ error }}
    </p>
    <p
      v-else
      class="permission-editor__hint"
    >
      权限标识用于后端鉴权；中文名称会展示在菜单和角色授权中，例如
      <code>file:storage:view</code> 对应“查询存储配置”。
    </p>
  </div>
</template>

<style scoped>
.permission-editor {
  width: 100%;
}

.permission-editor__head,
.permission-editor__row {
  display: grid;
  grid-template-columns: minmax(180px, 1.15fr) minmax(140px, 0.85fr) minmax(180px, 1fr) 34px;
  gap: 8px;
  align-items: center;
}

.permission-editor__head {
  margin-bottom: 7px;
  padding: 0 2px;
  color: #7b889c;
  font-size: 11px;
}

.permission-editor__row + .permission-editor__row {
  margin-top: 8px;
}

.permission-editor__add {
  margin-top: 10px;
}

.permission-editor__error,
.permission-editor__hint {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.7;
}

.permission-editor__error {
  color: #dc4c42;
}

.permission-editor__hint {
  color: #7b889c;
}

.permission-editor__hint code {
  color: #2563eb;
}

@media (max-width: 760px) {
  .permission-editor__head {
    display: none;
  }

  .permission-editor__row {
    grid-template-columns: minmax(0, 1fr) 34px;
  }

  .permission-editor__row :deep(.n-input) {
    grid-column: 1;
  }

  .permission-editor__row :deep(.n-button) {
    grid-column: 2;
    grid-row: 1;
  }
}
</style>

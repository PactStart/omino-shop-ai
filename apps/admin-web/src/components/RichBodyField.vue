<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'

const body = defineModel<string>('body', { required: true })
const format = defineModel<'plain' | 'html'>('format', { required: true })
const editor = ref<HTMLDivElement | null>(null)

watch(format, async (value) => {
  if (value !== 'html') return
  await nextTick()
  if (editor.value) editor.value.innerHTML = body.value
})

watch(body, (value) => {
  if (format.value !== 'html' || !editor.value) return
  if (editor.value.innerHTML !== value) editor.value.innerHTML = value
})

function onInput(): void {
  body.value = editor.value?.innerHTML ?? ''
}

function command(name: string, value?: string): void {
  editor.value?.focus()
  document.execCommand(name, false, value)
  onInput()
}

async function link(): Promise<void> {
  try {
    const { value } = await ElMessageBox.prompt('只接受 http 或 https', '插入链接', {
      inputValue: 'https://',
      confirmButtonText: '插入',
      cancelButtonText: '取消',
    })
    if (value.trim()) command('createLink', value.trim())
  } catch {
    // 取消插入时留在编辑器里。
  }
}

function onFormat(value: string | number | boolean | undefined): void {
  if (value === 'html') useHtml()
  else usePlain()
}

function usePlain(): void {
  if (format.value === 'plain') return
  const box = document.createElement('div')
  box.innerHTML = body.value
  body.value = (box.textContent || '').replace(/\u00a0/g, ' ').trim()
  format.value = 'plain'
}

function useHtml(): void {
  if (format.value === 'html') return
  const text = body.value.trim()
  body.value = text ? `<p>${escapeText(text)}</p>` : ''
  format.value = 'html'
}

function escapeText(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
</script>

<template>
  <div class="rich">
    <el-radio-group :model-value="format" size="small" @change="onFormat">
      <el-radio-button value="plain">纯文本</el-radio-button>
      <el-radio-button value="html">富文本</el-radio-button>
    </el-radio-group>
    <el-input v-if="format === 'plain'" v-model="body" type="textarea" :rows="6" />
    <div v-else class="html">
      <div class="tools">
        <el-button size="small" native-type="button" @click="command('bold')">加粗</el-button>
        <el-button size="small" native-type="button" @click="command('italic')">斜体</el-button>
        <el-button size="small" native-type="button" @click="command('insertUnorderedList')">列表</el-button>
        <el-button size="small" native-type="button" @click="command('insertOrderedList')">编号</el-button>
        <el-button size="small" native-type="button" @click="link">链接</el-button>
      </div>
      <div ref="editor" class="sheet" contenteditable="true" @input="onInput" />
      <p class="hint">只保留段落、换行、加粗、斜体、列表和链接。图片和脚本不会保存。</p>
    </div>
  </div>
</template>

<style scoped>
.rich {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.html {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.tools {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.sheet {
  min-height: 140px;
  padding: 8px 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-blank);
  outline: none;
}
.hint {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

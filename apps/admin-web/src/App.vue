<template>
  <router-view />
</template>

<style>
html,
body,
#app {
  height: 100%;
  margin: 0;
}
html {
  color-scheme: light;
}
html.dark {
  color-scheme: dark;
}
body {
  background: var(--el-bg-color-page);
  color: var(--el-text-color-primary);
}
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.page h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
</style>

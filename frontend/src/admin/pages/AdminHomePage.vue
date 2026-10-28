<template>
  <section class="page">
    <p class="eyebrow">管理端</p>
    <h1>运营控制台</h1>
    <p class="lead">题库管理、审核、用户管理、举报处理与基础看板将在 P1/P2 阶段接入。</p>
  </section>
</template>

<style scoped>
.page {
  width: min(56rem, 100%);
  margin: 0 auto;
  padding: clamp(1.5rem, 4vw, 2.5rem);
  border: 1px solid #dbe2ef;
  border-radius: 1rem;
  background: #ffffff;
}

.eyebrow {
  margin: 0 0 0.5rem;
  color: #3157c8;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  font-size: clamp(1.5rem, 3vw, 2rem);
}

.lead {
  margin: 1rem 0 0;
  color: #5b6780;
  line-height: 1.7;
}
</style>

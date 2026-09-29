<script setup>
// Cards for every course in one category (or all categories).
import { computed } from 'vue'
import { withBase } from 'vitepress'
import { courses } from '../../courses.mjs'

const props = defineProps({ category: { type: String, default: '' } })
const list = computed(() => (props.category ? courses.filter((c) => c.category === props.category) : courses))
</script>

<template>
  <div v-if="list.length" class="cards">
    <a v-for="course in list" :key="course.link" class="card" :href="withBase(course.link)">
      <h3>{{ course.title }}</h3>
      <p>{{ course.description }}</p>
      <span class="card__meta">{{ course.parts.filter((p) => !p.soon).length }} of {{ course.parts.length }} parts published</span>
    </a>
  </div>
  <p v-else class="empty-note">Courses coming soon. Check back shortly.</p>
</template>

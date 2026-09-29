<script setup>
// The list of all parts in a course, shown at the top of every lesson,
// with the current part marked "we are here".
import { computed } from 'vue'
import { useRoute, withBase } from 'vitepress'
import { courses } from '../../courses.mjs'

const props = defineProps({ course: { type: String, required: true } })
const route = useRoute()

const data = computed(() => courses.find((c) => c.link === props.course))
const here = (link) => route.path.replace(/\.html$/, '').endsWith(link)
</script>

<template>
  <nav v-if="data" class="series-nav" aria-label="Parts in this series">
    <p class="series-nav__title">This article is part of a series</p>
    <ol>
      <li v-for="part in data.parts" :key="part.link" :class="{ here: here(part.link) }">
        <span v-if="here(part.link)">{{ part.title }} <em>(we are here)</em></span>
        <span v-else-if="part.soon" class="soon">{{ part.title }} <em>(coming soon)</em></span>
        <a v-else :href="withBase(part.link)">{{ part.title }}</a>
      </li>
    </ol>
  </nav>
</template>

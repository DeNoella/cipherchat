<script setup>
// Home page: one card per category, with its latest courses.
import { withBase } from 'vitepress'
import { categories, coursesIn } from '../../courses.mjs'

const latest = (id) => coursesIn(id).slice(-3).reverse()
</script>

<template>
  <div class="cards">
    <div v-for="cat in categories" :key="cat.id" class="card card--category">
      <h3><a :href="withBase(cat.link)">{{ cat.title }}</a></h3>
      <p>{{ cat.blurb }}</p>
      <ul v-if="latest(cat.id).length">
        <li v-for="course in latest(cat.id)" :key="course.link">
          <a :href="withBase(course.link)">{{ course.title }}</a>
        </li>
      </ul>
      <p v-else class="empty-note">Courses coming soon.</p>
      <a class="card__more" :href="withBase(cat.link)">See all {{ cat.title.toLowerCase() }} courses →</a>
    </div>
  </div>
</template>

import DefaultTheme from 'vitepress/theme'
import SeriesNav from './components/SeriesNav.vue'
import CourseCards from './components/CourseCards.vue'
import CategoryCards from './components/CategoryCards.vue'
import './custom.css'

export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component('SeriesNav', SeriesNav)
    app.component('CourseCards', CourseCards)
    app.component('CategoryCards', CategoryCards)
  },
}

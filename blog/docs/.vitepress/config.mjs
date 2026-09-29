import { defineConfig } from 'vitepress'
import { withMermaid } from 'vitepress-plugin-mermaid'
import { categories, courses } from './courses.mjs'

// The site is served from https://angelabs-png.github.io/cipherchat/
const base = '/cipherchat/'

// One sidebar per course, built from courses.mjs.
function sidebar() {
  const result = {}
  for (const course of courses) {
    result[course.link] = [
      {
        text: course.title,
        items: [
          { text: 'Course overview', link: course.link },
          ...course.parts.filter((p) => !p.soon).map((p) => ({ text: p.title, link: p.link })),
        ],
      },
    ]
  }
  return result
}

export default withMermaid(
  defineConfig({
    base,
    lang: 'en-GB',
    title: 'Angelabs',
    description: 'A learning blog: cybersecurity and software development, explained from the beginning.',
    cleanUrls: true,
    // Lessons link to the app running locally (http://localhost:3000).
    ignoreDeadLinks: 'localhostLinks',
    lastUpdated: true,
    // Light, warm theme only: no dark mode, so there is no black anywhere.
    appearance: false,
    head: [
      ['link', { rel: 'icon', href: `${base}favicon.svg`, type: 'image/svg+xml' }],
      ['link', { rel: 'preconnect', href: 'https://fonts.googleapis.com' }],
      ['link', { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' }],
      [
        'link',
        {
          rel: 'stylesheet',
          href: 'https://fonts.googleapis.com/css2?family=Source+Serif+4:ital,opsz,wght@0,8..60,400;0,8..60,600;1,8..60,400&family=Inter:wght@400;500;600&family=JetBrains+Mono:wght@400;500&display=swap',
        },
      ],
    ],
    markdown: {
      lineNumbers: true,
      toc: { level: [2] },
      theme: 'catppuccin-latte',
    },
    mermaid: {
      theme: 'base',
      themeVariables: {
        primaryColor: '#f6ede1',
        primaryBorderColor: '#c9b49a',
        primaryTextColor: '#4a3b30',
        lineColor: '#8a7462',
        secondaryColor: '#eef1e8',
        tertiaryColor: '#fbf7f1',
        noteBkgColor: '#f3e6d6',
        noteTextColor: '#4a3b30',
        noteBorderColor: '#c9b49a',
        actorBkg: '#f6ede1',
        actorBorder: '#c9b49a',
        actorTextColor: '#4a3b30',
        signalColor: '#6b5646',
        signalTextColor: '#4a3b30',
        labelBoxBkgColor: '#f6ede1',
        labelTextColor: '#4a3b30',
      },
    },
    themeConfig: {
      logo: { src: '/favicon.svg', alt: '' },
      nav: [
        { text: 'Home', link: '/' },
        ...categories.map((c) => ({ text: c.title, link: c.link, activeMatch: `^${c.link}` })),
        { text: 'About', link: '/about' },
      ],
      sidebar: sidebar(),
      outline: { level: 2, label: 'On this page' },
      search: { provider: 'local' },
      socialLinks: [{ icon: 'github', link: 'https://github.com/angelabs-png' }],
      docFooter: { prev: 'Previous', next: 'Next' },
      footer: {
        message: 'Written to learn, and to explain what I built.',
      },
    },
  }),
)

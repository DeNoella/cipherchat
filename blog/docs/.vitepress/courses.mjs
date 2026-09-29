// The one list of every course on the blog.
// The home page cards, the category pages, the sidebars and the
// "parts in this series" box at the top of each lesson are all built from it.
// To add a course or a lesson, add an entry here (see HOW_TO_ADD_A_COURSE.md).
// A part marked `soon: true` is listed as "coming soon" and not linked yet.

export const categories = [
  {
    id: 'cybersecurity',
    title: 'Cybersecurity',
    link: '/cybersecurity/',
    blurb: 'Encryption, keys and secure apps, explained from zero with real code.',
  },
  {
    id: 'software-development',
    title: 'Software Development',
    link: '/software-development/',
    blurb: 'Building real applications, one layer at a time.',
  },
]

export const courses = [
  {
    category: 'cybersecurity',
    title: 'Learn PGP from the beginning',
    link: '/cybersecurity/pgp-from-the-beginning/',
    description:
      'PGP from zero, proven line by line with CipherChat, an end-to-end encrypted chat app built with Spring Boot and Next.js.',
    parts: [
      { title: 'Part I: Why encryption, and the two kinds of keys', link: '/cybersecurity/pgp-from-the-beginning/part-1', soon: true },
      { title: 'Part II: How PGP actually protects a message', link: '/cybersecurity/pgp-from-the-beginning/part-2', soon: true },
      { title: 'Part III: PGP inside CipherChat, end to end', link: '/cybersecurity/pgp-from-the-beginning/part-3', soon: true },
      { title: 'Part IV: Spring Boot architecture, layer by layer', link: '/cybersecurity/pgp-from-the-beginning/part-4', soon: true },
      { title: 'Part V: Limitations, proof, and interview prep', link: '/cybersecurity/pgp-from-the-beginning/part-5', soon: true },
    ],
  },
]

export const coursesIn = (categoryId) => courses.filter((c) => c.category === categoryId)

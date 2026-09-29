# How to add a course or a lesson

The blog lives in this `blog/` folder. It is built with [VitePress](https://vitepress.dev): every lesson is one Markdown (`.md`) file, and GitHub publishes the site automatically when you push.

Live site: <https://angelabs-png.github.io/cipherchat/>

## Where things are

```
blog/
├── HOW_TO_ADD_A_COURSE.md        ← this file
├── package.json
└── docs/
    ├── index.md                  ← Home page
    ├── about.md                  ← About page
    ├── cybersecurity/
    │   ├── index.md              ← lists all cybersecurity courses (automatic)
    │   └── pgp-from-the-beginning/
    │       ├── index.md          ← course landing page
    │       ├── part-1.md         ← one file per lesson
    │       └── ...
    ├── software-development/
    │   └── index.md              ← lists all software development courses (automatic)
    ├── public/screenshots/       ← images (use them as /screenshots/name.png)
    └── .vitepress/
        ├── courses.mjs           ← ⭐ THE LIST OF ALL COURSES AND LESSONS
        └── config.mjs            ← site settings (you rarely need this)
```

## Preview the blog on your computer

```bash
cd blog
npm install      # first time only
npm run dev
```

Open the address it prints (usually <http://localhost:5173/cipherchat/>). The page reloads every time you save a file.

## Add a new lesson to an existing course

1. **Copy a template.** Copy `docs/cybersecurity/pgp-from-the-beginning/part-1.md` and rename it, for example `part-6.md`.
2. **Edit the top of the file.** Change the `title:` line and the `# Heading`. Keep the `<SeriesNav course="..." />` line as it is: it draws the "parts in this series" box and marks "we are here" by itself.
3. **Register it in the menu.** Open `docs/.vitepress/courses.mjs` and add one line to the course's `parts` list:

   ```js
   { title: 'Part VI: My new lesson', link: '/cybersecurity/pgp-from-the-beginning/part-6' },
   ```

   The `link` is the file path inside `docs/`, without `.md`. Add `soon: true` to list a lesson as "coming soon" before it is written.

That one line puts the lesson in the sidebar, in the series box at the top of every part, and in the part count on the course cards.

## Add a new course

1. **Create a folder** inside the right category, for example `docs/software-development/spring-boot-from-the-beginning/`.
2. **Copy the course landing page** `docs/cybersecurity/pgp-from-the-beginning/index.md` into it as `index.md`, and edit the text. Change the `course="..."` value in `<SeriesNav />` to your new folder link, e.g. `/software-development/spring-boot-from-the-beginning/`.
3. **Add your lessons** (`part-1.md`, `part-2.md`, ...) as described above.
4. **Register the course** in `docs/.vitepress/courses.mjs` by adding an entry to `courses`:

   ```js
   {
     category: 'software-development',       // or 'cybersecurity'
     title: 'Spring Boot from the beginning',
     link: '/software-development/spring-boot-from-the-beginning/',
     description: 'One sentence shown on the course card.',
     parts: [
       { title: 'Part I: ...', link: '/software-development/spring-boot-from-the-beginning/part-1' },
     ],
   },
   ```

The course now appears automatically on the **Home** page card, on its **category** page, and gets its own sidebar. The "Courses coming soon" note on Software Development disappears by itself once that category has a course.

## Add a new category (optional)

1. Add an entry to `categories` in `courses.mjs` (`id`, `title`, `link`, `blurb`).
2. Create `docs/<id>/index.md`. Copy `docs/software-development/index.md` and change the `category="..."` value.

The top menu picks it up automatically.

## Writing tips (the house style)

- Start with "I assume nothing", then **"In this article we will cover"**, a bolded list.
- Add a table of contents with `[[toc]]`.
- Explain **why** before **how**.
- After each code block, write **"Let's break it down"** with one bullet per important line.
- Short tip or warning: start the line with `>`.
- Click-to-reveal answer:

  ```md
  ::: details Answer
  The answer goes here.
  :::
  ```

- Diagram: use a fenced block with the language `mermaid`.
- Images: put the file in `docs/public/screenshots/` and write `![What it shows](/screenshots/file.png)`.

## Publish

```bash
git add blog
git commit -m "docs(blog): add part 6 on ..."
git push
```

The **Deploy blog** GitHub Action (`.github/workflows/blog.yml`) builds and publishes the site in about a minute. Watch it under the repository's **Actions** tab. If the build fails, it is almost always a broken link: the log names the file and the link.

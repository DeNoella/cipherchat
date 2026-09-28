// End-to-end check of the whole encrypted flow in a real browser (Chrome via playwright-core).
// Requires the stack running on a FRESH database (usernames alice/bob/carol are created):
//   docker compose up -d   then   npm run test:e2e
// Env: BASE_URL (default http://localhost:3000), CHROME_PATH (default /usr/bin/google-chrome).
import { chromium } from 'playwright-core';
import fs from 'node:fs';
const BASE = process.env.BASE_URL ?? 'http://localhost:3000';
const shots = process.argv[2] ?? 'e2e-screenshots';
fs.mkdirSync(shots, { recursive: true });
const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH ?? '/usr/bin/google-chrome', headless: true });
const problems = [];
async function newUser() {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, acceptDownloads: true });
  const page = await ctx.newPage();
  page.on('console', (m) => { if (m.type() === 'error' || /Content Security Policy/i.test(m.text())) problems.push(`[console] ${m.text()}`); });
  page.on('pageerror', (e) => problems.push(`[pageerror] ${e.message}`));
  return page;
}
const step = (s) => console.log('✓', s);
async function register(page, name, shot) {
  await page.goto(BASE + '/register');
  await page.fill('input[name=username]', name);
  await page.fill('input[name=password]', `${name} password 123`);
  await page.fill('input[name=passphrase]', `${name} long passphrase`);
  await page.fill('input[name=confirm]', `${name} long passphrase`);
  if (shot) await page.screenshot({ path: `${shots}/register.png` });
  await page.click('button[type=submit]');
  await page.waitForURL('**/profile?welcome=1', { timeout: 20000 });
  await page.getByText('Back up your key now').waitFor();
}
const alice = await newUser();
const bob = await newUser();
await register(alice, 'alice', true); step('alice registered (key generated in browser)');
await register(bob, 'bob'); step('bob registered');
await alice.screenshot({ path: `${shots}/profile.png` });

// Validation + wrong-passphrase paths
const v = await newUser();
await v.goto(BASE + '/register');
await v.fill('input[name=username]', 'carol');
await v.fill('input[name=password]', 'same password 1');
await v.fill('input[name=passphrase]', 'same password 1');
await v.fill('input[name=confirm]', 'same password 1');
await v.click('button[type=submit]');
await v.getByText('Must be different from your password').waitFor(); step('passphrase must differ from password');

// bob opens the chat first to test real-time delivery
async function openChat(page, peer) {
  await page.getByRole('link', { name: 'Chats' }).click();
  await page.fill('input[name=search]', peer.slice(0, 2));
  await page.getByRole('link', { name: peer, exact: true }).click();
  await page.waitForURL(`**/chats/${peer}`);
}
await openChat(bob, 'alice');
await bob.getByText('Say hello').waitFor(); step('bob opened empty chat');

await openChat(alice, 'bob');
await alice.getByText('Say hello').waitFor();
await alice.fill('textarea', 'hello bob, this is secret');
await alice.click('button[type=submit]');
await alice.getByText('hello bob, this is secret').waitFor(); step('alice sent message');
await bob.getByText('hello bob, this is secret').waitFor({ timeout: 10000 }); step('bob received it in real time (WebSocket)');
await bob.getByText('Verified').first().waitFor(); step('bob sees Verified signature');

// Attachment
const content = Buffer.from('top secret file contents ' + Date.now());
await alice.setInputFiles('input[type=file]', { name: 'plan.txt', mimeType: 'text/plain', buffer: content });
await alice.fill('textarea', 'see attached');
await alice.click('button[type=submit]');
await bob.getByText('plan.txt').waitFor({ timeout: 10000 }); step('bob received attachment message');
const [download] = await Promise.all([bob.waitForEvent('download'), bob.getByText('plan.txt').click()]);
const got = fs.readFileSync(await download.path());
if (!got.equals(content)) throw new Error('attachment mismatch'); step('attachment decrypted in browser, bytes match');

await bob.fill('textarea', 'got it, thanks!');
await bob.click('button[type=submit]');
await alice.getByText('got it, thanks!').waitFor({ timeout: 10000 }); step('reply delivered');
await alice.getByText('Key fingerprint').click();
await alice.screenshot({ path: `${shots}/chat.png` });

// Reload -> key locked -> wrong passphrase -> correct
await bob.reload();
await bob.waitForURL('**/login?next=**');
await bob.getByText('Unlock your key').waitFor();
await bob.fill('input[name=passphrase]', 'wrong wrong wrong');
await bob.click('button[type=submit]');
await bob.getByText('Wrong passphrase').waitFor(); step('wrong passphrase rejected');
await bob.screenshot({ path: `${shots}/unlock-error.png` });
await bob.fill('input[name=passphrase]', 'bob long passphrase');
await bob.click('button[type=submit]');
await bob.waitForURL('**/chats/alice');
await bob.getByText('hello bob, this is secret').waitFor(); step('history decrypts after unlock');

// Fresh device login for alice -> must import backup
const aliceBackup = await (async () => {
  await alice.getByRole('link', { name: 'Profile' }).click();
  const [d] = await Promise.all([alice.waitForEvent('download'), alice.getByText('Export private key backup').click()]);
  return fs.readFileSync(await d.path(), 'utf8');
})();
if (!aliceBackup.includes('BEGIN PGP PRIVATE KEY BLOCK')) throw new Error('bad backup');
const fresh = await newUser();
await fresh.goto(BASE + '/login');
await fresh.fill('input[name=username]', 'alice');
await fresh.fill('input[name=password]', 'alice password 123');
await fresh.fill('input[name=passphrase]', 'alice long passphrase');
await fresh.click('button[type=submit]');
await fresh.getByText('Import your key').waitFor(); step('new device asks for key backup');
await fresh.setInputFiles('input[name=backup]', { name: 'backup.asc', mimeType: 'text/plain', buffer: Buffer.from(aliceBackup) });
await fresh.click('button[type=submit]');
await fresh.waitForURL('**/chats');
await fresh.getByRole('link', { name: 'bob' }).click();
await fresh.getByText('got it, thanks!').waitFor(); step('imported backup decrypts history on new device');

// Mobile screenshots (signed-out redirect + chat)
const m = await newUser();
await m.setViewportSize({ width: 390, height: 844 });
await m.goto(BASE + '/chats/alice');
await m.waitForURL('**/login');
await m.screenshot({ path: `${shots}/mobile-login.png` });
await fresh.setViewportSize({ width: 390, height: 844 });
await fresh.screenshot({ path: `${shots}/mobile-chat.png` });

await browser.close();
if (problems.length) { console.error('PROBLEMS:\n' + problems.join('\n')); process.exit(1); }
console.log('no console errors / CSP violations');

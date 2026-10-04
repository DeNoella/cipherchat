// End-to-end check of the whole encrypted flow in a real browser (Chrome via playwright-core).
// Requires the stack running:   docker compose up -d   then   npm run test:e2e
// Usernames get a random suffix, so it can run again on the same database.
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
const run = Math.random().toString(36).slice(2, 7);
const ALICE = `alice_${run}`, BOB = `bob_${run}`, CAROL = `carol_${run}`;
const password = (name) => `${name} password 123`;
const passphrase = (name) => `${name} long passphrase`;
async function register(page, name, shot) {
  await page.goto(BASE + '/register');
  await page.fill('input[name=username]', name);
  await page.fill('input[name=password]', password(name));
  await page.fill('input[name=passphrase]', passphrase(name));
  await page.fill('input[name=confirm]', passphrase(name));
  if (shot) await page.screenshot({ path: `${shots}/register.png` });
  await page.click('button[type=submit]');
  await page.waitForURL('**/profile?welcome=1', { timeout: 20000 });
  await page.getByText('Remember your key passphrase').waitFor();
}
async function login(page, name) {
  await page.goto(BASE + '/login');
  await page.fill('input[name=username]', name);
  await page.fill('input[name=password]', password(name));
  if (await page.locator('input[name=passphrase]').count()) throw new Error('login form must not ask for the passphrase');
  await page.click('button[type=submit]');
}
const alice = await newUser();
const bob = await newUser();
await register(alice, ALICE, true); step('alice registered (key generated in browser, backup uploaded)');
await register(bob, BOB); step('bob registered');
await alice.screenshot({ path: `${shots}/profile.png` });

// Validation + wrong-passphrase paths
const v = await newUser();
await v.goto(BASE + '/register');
await v.fill('input[name=username]', CAROL);
await v.fill('input[name=password]', 'same password 1');
await v.fill('input[name=passphrase]', 'same password 1');
await v.fill('input[name=confirm]', 'same password 1');
await v.click('button[type=submit]');
await v.getByText('Must be different from your password').waitFor(); step('passphrase must differ from password');

// bob opens the chat first to test real-time delivery
async function openChat(page, peer) {
  await page.getByRole('link', { name: 'Chats' }).click();
  await page.fill('input[name=search]', peer);
  await page.getByRole('link', { name: peer, exact: true }).click();
  await page.waitForURL(`**/chats/${peer}`);
}
await openChat(bob, ALICE);
await bob.getByText('Say hello').waitFor(); step('bob opened empty chat');

await openChat(alice, BOB);
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

// Reload -> the device key unlocks the private key again, no prompt
await bob.reload();
await bob.waitForURL(`**/chats/${ALICE}`);
await bob.getByText('hello bob, this is secret').waitFor(); step('reload: history decrypts with no passphrase prompt');

// Sign out (keep device) and sign in again on the same browser: password only
await bob.getByText('Sign out', { exact: true }).first().click();
await bob.getByRole('button', { name: /Keep my key/ }).click();
await bob.waitForURL('**/login');
await login(bob, BOB);
await bob.waitForURL('**/chats');
step('same browser: signed in with password only');

// New browser for alice: passphrase asked once, wrong one rejected with a clear message
const fresh = await newUser();
await login(fresh, ALICE);
await fresh.getByText('Set up this browser').waitFor(); step('new browser asks for the key passphrase');
await fresh.fill('input[name=passphrase]', 'wrong wrong wrong');
await fresh.click('button[type=submit]');
await fresh.getByText('That passphrase does not unlock your key backup').waitFor(); step('wrong passphrase rejected');
await fresh.screenshot({ path: `${shots}/unlock-error.png` });
await fresh.fill('input[name=passphrase]', passphrase(ALICE));
await fresh.click('button[type=submit]');
await fresh.waitForURL('**/chats');
await fresh.getByRole('link', { name: BOB }).click();
await fresh.getByText('got it, thanks!').waitFor(); step('backup from server decrypts history on new browser');

// That browser is now set up: next sign-in needs no passphrase
await fresh.getByText('Sign out', { exact: true }).first().click();
await fresh.getByRole('button', { name: /Keep my key/ }).click();
await login(fresh, ALICE);
await fresh.waitForURL('**/chats'); step('new browser remembered: password only');

// Forget this device -> passphrase asked again
await fresh.getByText('Sign out', { exact: true }).first().click();
await fresh.getByRole('button', { name: /forget this device/ }).click();
await login(fresh, ALICE);
await fresh.getByText('Set up this browser').waitFor(); step('forgotten device asks for the passphrase again');
await fresh.fill('input[name=passphrase]', passphrase(ALICE));
await fresh.click('button[type=submit]');
await fresh.waitForURL('**/chats');
await fresh.getByRole('link', { name: BOB }).click();
await fresh.getByText('got it, thanks!').waitFor();

// Site data cleared while signed in -> clear "missing key" message
await bob.evaluate(() => new Promise((resolve) => { const r = indexedDB.deleteDatabase('cipherchat'); r.onsuccess = r.onerror = r.onblocked = resolve; }));
await bob.goto(BASE + `/chats/${ALICE}`);
await bob.waitForURL('**/login?device=missing**');
await bob.getByText('Your private key is not on this browser any more').waitFor(); step('missing local key explained');

// Mobile screenshots (signed-out redirect + chat)
const m = await newUser();
await m.setViewportSize({ width: 390, height: 844 });
await m.goto(BASE + `/chats/${ALICE}`);
await m.waitForURL('**/login');
await m.screenshot({ path: `${shots}/mobile-login.png` });
await fresh.setViewportSize({ width: 390, height: 844 });
await fresh.screenshot({ path: `${shots}/mobile-chat.png` });

await browser.close();
if (problems.length) { console.error('PROBLEMS:\n' + problems.join('\n')); process.exit(1); }
console.log('no console errors / CSP violations');

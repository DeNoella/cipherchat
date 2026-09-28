// Regenerates the OpenPGP.js test fixtures in this folder (throwaway keys, test passphrases).
// Usage: npm i openpgp@6 && node generate-fixtures.mjs .
import * as openpgp from 'openpgp';
import fs from 'node:fs';
const out = process.argv[2];
const gen = (name) => openpgp.generateKey({ type: 'ecc', curve: 'curve25519Legacy', userIDs: [{ name }], passphrase: 'test passphrase ' + name, format: 'armored' });
const users = {};
for (const n of ['alice', 'bob', 'carol']) {
  users[n] = await gen(n);
  fs.writeFileSync(`${out}/${n}.pub.asc`, users[n].publicKey);
}
const pub = async (n) => openpgp.readKey({ armoredKey: users[n].publicKey });
const priv = await openpgp.decryptKey({ privateKey: await openpgp.readPrivateKey({ armoredKey: users.alice.privateKey }), passphrase: 'test passphrase alice' });
const msg = await openpgp.encrypt({ message: await openpgp.createMessage({ text: JSON.stringify({ v: 1, text: 'hello bob' }) }), encryptionKeys: [await pub('bob'), await pub('alice')], signingKeys: priv });
fs.writeFileSync(`${out}/alice-to-bob.asc`, msg);
const toCarol = await openpgp.encrypt({ message: await openpgp.createMessage({ text: 'hi carol' }), encryptionKeys: [await pub('carol'), await pub('alice')], signingKeys: priv });
fs.writeFileSync(`${out}/alice-to-carol.asc`, toCarol);
const bin = await openpgp.encrypt({ message: await openpgp.createMessage({ binary: new Uint8Array(2048).fill(7) }), encryptionKeys: [await pub('bob'), await pub('alice')], signingKeys: priv, format: 'binary' });
fs.writeFileSync(`${out}/alice-to-bob.bin`, bin);
const signedOnly = await openpgp.sign({ message: await openpgp.createMessage({ text: 'not encrypted' }), signingKeys: priv });
fs.writeFileSync(`${out}/signed-only.asc`, signedOnly);
console.log('ok');

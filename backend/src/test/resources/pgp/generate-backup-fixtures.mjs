// Regenerates the key-backup fixtures (a separate throwaway user "dave", so the other fixtures stay unchanged).
// Usage: npm i openpgp@6 && node generate-backup-fixtures.mjs .
//   dave.pub.asc          public key
//   dave.key-backup.asc   private key locked with the passphrase "test passphrase dave" (what the browser uploads)
//   dave.unprotected.asc  the same private key WITHOUT a passphrase (the server must refuse it)
import * as openpgp from 'openpgp';
import fs from 'node:fs';
const out = process.argv[2];
const passphrase = 'test passphrase dave';
const { publicKey, privateKey } = await openpgp.generateKey({
  type: 'ecc', curve: 'curve25519Legacy', userIDs: [{ name: 'dave' }], passphrase, format: 'armored',
  config: { s2kIterationCountByte: 255 },
});
fs.writeFileSync(`${out}/dave.pub.asc`, publicKey);
fs.writeFileSync(`${out}/dave.key-backup.asc`, privateKey);
const unlocked = await openpgp.decryptKey({ privateKey: await openpgp.readPrivateKey({ armoredKey: privateKey }), passphrase });
fs.writeFileSync(`${out}/dave.unprotected.asc`, unlocked.armor());
console.log('ok');

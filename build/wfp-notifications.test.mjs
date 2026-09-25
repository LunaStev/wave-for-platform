import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';

const read = path => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

test('retained Kotlin notification groups resolve their message bundle keys', () => {
  const descriptor = read('plugins/kotlin/plugin/common/resources/META-INF/kotlin-core.xml');
  const bundles = new Map([
    ['messages.KotlinBundle', read('plugins/kotlin/base/resources/resources-en/messages/KotlinBundle.properties')],
    ['messages.KotlinProjectConfigurationBundle', read('plugins/kotlin/project-configuration/resources/messages/KotlinProjectConfigurationBundle.properties')],
  ]);
  const defaultBundle = descriptor.match(/<resource-bundle>([^<]+)<\/resource-bundle>/)[1];
  const groups = [...descriptor.matchAll(/<notificationGroup\s+([^>]+)\/>/g)];
  assert.ok(groups.length > 0);
  for (const [, attributes] of groups) {
    const values = Object.fromEntries([...attributes.matchAll(/(\w+)="([^"]*)"/g)].map(([, key, value]) => [key, value]));
    assert.notEqual(values.id, 'Kotlin EAP');
    const bundle = bundles.get(values.bundle ?? defaultBundle);
    assert.ok(bundle, `Missing bundle for ${values.id}`);
    assert.ok(bundle.split(/\r?\n/).some(line => line.startsWith(`${values.key}=`)), `Missing key ${values.key}`);
  }
});

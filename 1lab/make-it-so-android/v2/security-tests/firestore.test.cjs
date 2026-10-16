/**
 * ЛАБА №1 — проверка Firestore Security Rules БЕЗ Android UI и БЕЗ реального Firebase.
 * Запуск из каталога v2 через Firebase Emulator Suite, см. docs/TESTS_RU.md.
 * Проверяем правило authorization, а НЕ факт видимости задачи на экране.
 *
 * ВНИМАНИЕ: assertFails() = запрос отвергнут с permission-denied;
 *             assertSucceeds() = запрос завершился успешно.
 */
const { test, before, beforeEach, after } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const {
  initializeTestEnvironment, assertFails, assertSucceeds,
} = require('@firebase/rules-unit-testing');
const {
  doc, getDoc, setDoc, updateDoc, deleteDoc, collection, getDocs,
} = require('firebase/firestore');

let env;
// Полностью валидный документ. Каждый отрицательный тест меняет ровно одно условие.
const valid = ownerId => ({ ownerId, title: 'Моя задача', priority: 0, completed: false, flagged: false });
// Имена пользователей в тестах — НЕ настоящие почтовые аккаунты.
const document = (db, uid, id = 'sample') => doc(db, 'users', uid, 'todoitems', id);
const list = (db, uid) => collection(db, 'users', uid, 'todoitems');
// Имитация ID token Firebase с конкретным uid и провайдером входа.
const user = (uid, provider = 'password') => env.authenticatedContext(uid, {
  firebase: { sign_in_provider: provider },
}).firestore();

before(async () => {
  // Локальный проект demo-... и загруженные файлом правила: не подключаемся к production.
  env = await initializeTestEnvironment({
    projectId: 'demo-makeitso-security-lab',
    firestore: { rules: fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8') },
  });
});

beforeEach(async () => {
  // Каждый тест независим от порядка запуска: очищаем эмулятор, затем загружаем образец.
  await env.clearFirestore();
  // «Администратор тестов» имеет право подготовить данные, но обычный пользователь — нет.
  // Обход правил ЗДЕСЬ относится ТОЛЬКО к локальной подготовке тестового окружения.
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(document(context.firestore(), 'alice'), valid('alice'));
  });
});

after(async () => {
  if (env) await env.cleanup();
});

// Положительные проверки: владелец вправе читать/создавать/редактировать/удалять СВОЁ.
test('01 Alice reads her own existing document', async () => {
  await assertSucceeds(getDoc(document(user('alice'), 'alice')));
});
test('02 Alice can list only her own collection', async () => {
  await assertSucceeds(getDocs(list(user('alice'), 'alice')));
});
test('03 Alice creates a valid new item', async () => {
  await assertSucceeds(setDoc(document(user('alice'), 'alice', 'new'), valid('alice')));
});
test('04 Alice updates allowed field without changing owner', async () => {
  await assertSucceeds(updateDoc(document(user('alice'), 'alice'), {completed: true}));
});
test('05 Alice deletes own item', async () => {
  await assertSucceeds(deleteDoc(document(user('alice'), 'alice')));
});

// Отрицательные проверки: Bob не владеет документом Alice.
test('06 Bob cannot read Alice document', async () => {
  await assertFails(getDoc(document(user('bob', 'google.com'), 'alice')));
});
test('07 Bob cannot list Alice tasks', async () => {
  await assertFails(getDocs(list(user('bob'), 'alice')));
});
test('08 Bob cannot write document into Alice folder', async () => {
  await assertFails(setDoc(document(user('bob'), 'alice', 'forged'), valid('alice')));
});
test('09 Bob cannot change Alice item', async () => {
  await assertFails(updateDoc(document(user('bob'), 'alice'), {title: 'Взлом'}));
});
test('10 Bob cannot delete Alice item', async () => {
  await assertFails(deleteDoc(document(user('bob'), 'alice')));
});

// Нет авторизации — нет доступа даже если злоумышленник знает имя коллекции.
test('11 Anonymous (no Firebase session) denied', async () => {
  const guest = env.unauthenticatedContext().firestore();
  await assertFails(getDoc(document(guest, 'alice')));
  await assertFails(setDoc(document(guest, 'guest'), valid('guest')));
});
test('12 Firebase anonymous provider rejected', async () => {
  await assertFails(setDoc(document(user('anon', 'anonymous'), 'anon'), valid('anon')));
});

// Защищаем владельца/схему документа от подмены и лишних полей.
test('13 Owner cannot replace ownerId on update', async () => {
  await assertFails(updateDoc(document(user('alice'), 'alice'), {ownerId: 'bob'}));
});
test('14 Owner cannot create item with foreign ownerId', async () => {
  await assertFails(setDoc(document(user('alice'), 'alice', 'forged'), valid('bob')));
});
test('15 Extra field isAdmin forbidden', async () => {
  await assertFails(setDoc(document(user('alice'), 'alice', 'extra'), {...valid('alice'), isAdmin: true}));
});
test('16 Missing required completed field forbidden', async () => {
  const data = valid('alice'); delete data.completed;
  await assertFails(setDoc(document(user('alice'), 'alice', 'missing'), data));
});
test('17 Empty title forbidden', async () => {
  await assertFails(setDoc(document(user('alice'), 'alice', 'empty'), {...valid('alice'), title: ''}));
});
test('18 Title longer than 200 characters forbidden', async () => {
  await assertFails(setDoc(document(user('alice'), 'alice', 'long'), {...valid('alice'), title: 'x'.repeat(201)}));
});
test('19 Priority outside 0..3 forbidden', async () => {
  await assertFails(setDoc(document(user('alice'), 'alice', 'priority'), {...valid('alice'), priority: 9}));
});
test('20 Non-boolean completed forbidden', async () => {
  await assertFails(setDoc(document(user('alice'), 'alice', 'badtype'), {...valid('alice'), completed: 'true'}));
});
test('21 Legacy collection todolist inaccessible', async () => {
  await assertFails(setDoc(doc(user('alice'), 'todolist', 'old'), {owner: 'alice'}));
});

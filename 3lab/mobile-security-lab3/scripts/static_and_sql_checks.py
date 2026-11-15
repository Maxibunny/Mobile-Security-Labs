#!/usr/bin/env python3
"""Проверяет исходники ЛР3 без SDK: XML/TOML, схема и реальный SQL из DAO."""
from pathlib import Path
import re
import sqlite3
import tomllib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
code = ROOT / 'notes/src/main/java/edu/androidsec/notes'
source = lambda rel: (code / rel).read_text(encoding='utf-8')
print('PROJECT ROOT', ROOT)

files = list(ROOT.rglob('*.xml'))
for file in files:
    ET.parse(file)
print('PASS XML', len(files))
with (ROOT / 'gradle/libs.versions.toml').open('rb') as fh:
    versions = tomllib.load(fh)['versions']
assert versions['agp'] == '8.13.0'
assert versions['kotlin'] == '2.2.21'
print('PASS TOML / versions')

manifest = source('data/db/NotesDatabase.kt')
dao = source('data/db/NoteDao.kt')
repo = source('data/NotesRepository.kt')
validator = source('domain/NoteValidator.kt')
ui = source('ui/note/NoteScreen.kt')
assert '@RawQuery' not in dao
assert 'fallbackToDestructiveMigration' not in manifest
assert 'InputTransformation' in ui and 'TextFieldState' in ui and 'revertAllChanges()' in ui
assert "import androidx.compose.foundation.text.input.edit" not in ui  # edit — метод TextFieldState
assert 'FLAG_SECURE' in ui and 'EXTRA_IS_SENSITIVE' in ui
assert 'NoteValidator.validate(note)' in repo
assert 'UPDATE notes SET pending_delete_at' in dao
assert 'DELETE FROM notes WHERE id = :id AND pending_delete_at = :deadline' in dao
assert 'Room.databaseBuilder' in manifest and 'MIGRATION_1_2' in manifest
assert 'MAX_TAGS = 5' in validator and 'TAG_LIMIT = 20' in validator
print('PASS structural checks')

con = sqlite3.connect(':memory:')
con.execute('''CREATE TABLE notes (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    photo_path TEXT,
    updated_at INTEGER NOT NULL
)''')
con.executemany('INSERT INTO notes (id,title,body,photo_path,updated_at) VALUES (?,?,?,?,?)', [
    (17, 'old', 'saved', '/files/old.jpg', 10), (18, 'other', 'text', None, 20),
])
# Здесь исполняются непосредственно строки из Kotlin Migration, не их ручные копии.
block = manifest.split('override fun migrate(db: SupportSQLiteDatabase)', 1)[1].split('\n        }', 1)[0]
migrations = re.findall(r'db\.execSQL\("([^"]+)"\)', block)
assert len(migrations) == 3
for sql in migrations: con.execute(sql)
assert con.execute('SELECT id,title,body,photo_path,updated_at,tags,is_secret,pending_delete_at FROM notes WHERE id=17').fetchone() == (17,'old','saved','/files/old.jpg',10,'',0,0)
assert con.execute('SELECT count(*) FROM notes').fetchone()[0] == 2
print('PASS SQLite migration v1 -> v2 and preserved records')

con.executemany('INSERT INTO notes(title,body,updated_at) VALUES(?,?,?)', [
    ('100% done', 'ordinary', 1), ('x_y', 'ordinary', 2),
    ('folder\\sub', 'ordinary', 3), ('other', 'body%only', 4),
    ('safe', 'irrelevant', 5),
])
queries = re.findall(r'@Query\("""((?:(?!""").)*)"""\)\s*fun (searchByTitle|searchNewest|searchOldest)', dao, re.DOTALL)
assert len(queries) == 3
searches = {name: sql for sql, name in queries}
for name, sql in searches.items():
    assert "ESCAPE '\\'" in sql
    assert 'title LIKE :pattern' in sql and 'body LIKE :pattern' in sql
    assert 'ORDER BY' in sql
    for value, result in [('%', {'100% done','other'}), ('_', {'x_y'}), ('\\', {'folder\\sub'}), ("%' OR 1=1 --",set())]:
        pattern = '%' + ''.join('\\'+c if c in '%_\\' else c for c in value) + '%'
        got = {r[0] for r in con.execute('SELECT title FROM ('+sql+')', {'pattern': pattern})}
        assert got == result, (name, value, got, result)
print('PASS 3 real DAO search queries (wildcards/escape/injection probe)')

# Проверить SQL для исчезновения и отмены удаления прямо из DAO.
con.execute('UPDATE notes SET pending_delete_at = ? WHERE id = ? AND pending_delete_at = 0', (12345,17))
assert con.execute('SELECT count(*) FROM notes WHERE pending_delete_at=0 AND id=17').fetchone()[0] == 0
con.execute('UPDATE notes SET pending_delete_at = 0 WHERE id = ? AND pending_delete_at = ?', (17,12345))
assert con.execute('SELECT body FROM notes WHERE id=17').fetchone()[0] == 'saved'
con.execute('UPDATE notes SET pending_delete_at = ? WHERE id = ? AND pending_delete_at = 0', (12345,17))
con.execute('DELETE FROM notes WHERE id = ? AND pending_delete_at = ?', (17,12345))
assert con.execute('SELECT count(*) FROM notes WHERE id=17').fetchone()[0] == 0
print('PASS SQLite mark -> undo -> finalized delete')
print('PASS ALL (static and SQLite only, NOT Android/Gradle/emulator)')

package dev.vantage.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fixtures in {@code /sqlite} were written by SQLite itself (Python's sqlite3, page size 1024): {@code basic.db}
 * with 300 rows (interior pages), a 5000-character value (overflow pages), an INTEGER PRIMARY KEY, a column added with
 * ALTER TABLE and quoted names; {@code wal.db} whose last transaction ("b" renamed to "changed", "c" added) exists only
 * in {@code wal.db-wal}.
 */
class SqliteReaderTest {
	@TempDir
	Path dir;

	private Path fixture(String... names) throws IOException {
		for (String name : names) {
			try (InputStream in = SqliteReaderTest.class.getResourceAsStream("/sqlite/" + name)) {
				Files.copy(in, dir.resolve(name));
			}
		}
		return dir.resolve(names[0]);
	}

	@Test
	void readsAllRowsOfAMultiPageTable() throws IOException {
		SqliteReader reader = SqliteReader.open(fixture("basic.db"));
		assertEquals(List.of("id", "n", "f", "b", "t"), reader.columns("items"));
		List<Map<String, Object>> rows = reader.rows("items");
		assertEquals(300, rows.size());
		Map<String, Object> row = rows.get(4);
		assertEquals("id-004", row.get("id"));
		assertEquals(4L * 1000003, row.get("n"));
		assertEquals(1.0, row.get("f"));
		assertArrayEquals(new byte[] {4, 1, 2}, (byte[]) row.get("b"));
		assertEquals("text-4", row.get("t"));
		assertEquals(-3L * 70001, rows.get(3).get("n"));
		assertNull(rows.get(9).get("n"));
	}

	@Test
	void followsOverflowPages() throws IOException {
		SqliteReader reader = SqliteReader.open(fixture("basic.db"));
		assertEquals("x".repeat(5000), reader.rows("items").get(7).get("t"));
	}

	@Test
	void handlesRowidAliasesAddedColumnsAndQuotedNames() throws IOException {
		SqliteReader reader = SqliteReader.open(fixture("basic.db"));
		List<Map<String, Object>> aliased = reader.rows("aliased");
		assertEquals(5L, aliased.get(0).get("key"));
		assertEquals("forty-two", aliased.get(1).get("label"));
		List<Map<String, Object>> grown = reader.rows("grown");
		assertNull(grown.get(0).get("b"));
		assertEquals("bee", grown.get(1).get("b"));
		assertEquals(List.of("first col", "second"), reader.columns("with space"));
		assertEquals("Grüße ✓", reader.rows("with space").getFirst().get("first col"));
		assertTrue(reader.tables().containsAll(List.of("items", "aliased", "grown", "with space")));
	}

	@Test
	void appliesCommittedChangesFromTheWriteAheadLog() throws IOException {
		SqliteReader reader = SqliteReader.open(fixture("wal.db", "wal.db-wal"));
		List<String> names = new ArrayList<>();
		reader.rows("t").forEach(row -> names.add((String) row.get("name")));
		assertEquals(List.of("a", "changed", "c"), names);
	}

	@Test
	void withoutTheLogTheCheckpointedStateIsRead() throws IOException {
		SqliteReader reader = SqliteReader.open(fixture("wal.db"));
		List<String> names = new ArrayList<>();
		reader.rows("t").forEach(row -> names.add((String) row.get("name")));
		assertEquals(List.of("a", "b"), names);
	}

	@Test
	void ignoresAWriteAheadLogWithABrokenChecksum() throws IOException {
		Path db = fixture("wal.db", "wal.db-wal");
		Path wal = dir.resolve("wal.db-wal");
		byte[] bytes = Files.readAllBytes(wal);
		bytes[bytes.length - 10] ^= 0x55;
		Files.write(wal, bytes);
		List<String> names = new ArrayList<>();
		SqliteReader.open(db).rows("t").forEach(row -> names.add((String) row.get("name")));
		assertEquals(List.of("a", "b"), names);
	}

	@Test
	void rejectsOtherFilesAndUnknownTables() throws IOException {
		Path text = Files.writeString(dir.resolve("notes.db"), "not a database at all, just some text that is long enough to have a header");
		assertThrows(IOException.class, () -> SqliteReader.open(text));
		SqliteReader reader = SqliteReader.open(fixture("basic.db"));
		assertThrows(IOException.class, () -> reader.rows("missing"));
	}
}

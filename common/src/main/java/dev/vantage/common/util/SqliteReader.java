package dev.vantage.common.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Minimal read-only reader for SQLite database files (file format 3, https://www.sqlite.org/fileformat2.html),
 * including the committed frames of a write-ahead log ({@code <file>-wal}) next to it. It reads whole rowid tables,
 * which is all Vantage needs to import profiles from other launchers without shipping a native SQLite library.
 * It never writes and does not support indexes, WITHOUT ROWID tables or UTF-16 databases.
 */
public final class SqliteReader {
	private static final byte[] MAGIC = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);
	private static final long MAX_FILE_SIZE = 256L * 1024 * 1024;
	private static final int WAL_MAGIC_LE = 0x377f0682;
	private static final int WAL_MAGIC_BE = 0x377f0683;

	private final byte[] db;
	private final int pageSize;
	private final int usableSize;
	private final Map<Integer, byte[]> walPages;
	private final Map<String, Table> tables = new LinkedHashMap<>();

	private record Table(String name, int rootPage, List<String> columns, int rowidAlias, Set<Integer> realColumns) {
	}

	private SqliteReader(byte[] db, byte[] wal) throws IOException {
		this.db = db;
		if (db.length < 100 || !startsWith(db, MAGIC)) {
			throw new IOException("Not an SQLite database.");
		}
		int size = u16(db, 16);
		this.pageSize = size == 1 ? 65536 : size;
		if (pageSize < 512 || (pageSize & (pageSize - 1)) != 0) {
			throw new IOException("Damaged SQLite database (page size " + pageSize + ").");
		}
		this.usableSize = pageSize - (db[20] & 0xFF);
		if (u32(db, 56) > 1) {
			throw new IOException("Only UTF-8 SQLite databases are supported.");
		}
		this.walPages = readWal(wal);
		readSchema();
	}

	/** Reads {@code file} and, if present, {@code file-wal}. */
	public static SqliteReader open(Path file) throws IOException {
		byte[] db = read(file);
		Path walFile = file.resolveSibling(file.getFileName() + "-wal");
		byte[] wal = Files.isRegularFile(walFile) ? read(walFile) : new byte[0];
		return new SqliteReader(db, wal);
	}

	private static byte[] read(Path file) throws IOException {
		if (Files.size(file) > MAX_FILE_SIZE) {
			throw new IOException(file.getFileName() + " is too large to read.");
		}
		return Files.readAllBytes(file);
	}

	public Set<String> tables() {
		return tables.keySet();
	}

	public boolean hasTable(String name) {
		return tables.containsKey(name);
	}

	/** Column names of {@code table} in declaration order. */
	public List<String> columns(String name) throws IOException {
		return table(name).columns();
	}

	/** All rows of {@code table}; columns missing in old rows (added later with ALTER TABLE) are null. */
	public List<Map<String, Object>> rows(String name) throws IOException {
		Table table = table(name);
		List<Map<String, Object>> rows = new ArrayList<>();
		walk(table.rootPage(), new HashSet<>(), (rowid, payload) -> {
			List<Object> values = decodeRecord(payload);
			Map<String, Object> row = new LinkedHashMap<>();
			for (int i = 0; i < table.columns().size(); i++) {
				Object value = i < values.size() ? values.get(i) : null;
				if (i == table.rowidAlias()) {
					value = rowid;
				} else if (value instanceof Long whole && table.realColumns().contains(i)) {
					// SQLite stores REAL values without a fraction as integers; reading converts them back.
					value = whole.doubleValue();
				}
				row.put(table.columns().get(i), value);
			}
			rows.add(row);
		});
		return rows;
	}

	private Table table(String name) throws IOException {
		Table table = tables.get(name);
		if (table == null) {
			throw new IOException("The database has no table " + name + ".");
		}
		return table;
	}

	// ------------------------------------------------------------------------------------------------ schema

	private void readSchema() throws IOException {
		walk(1, new HashSet<>(), (rowid, payload) -> {
			List<Object> values = decodeRecord(payload);
			if (values.size() >= 5 && "table".equals(values.get(0)) && values.get(3) instanceof Long root && values.get(4) instanceof String sql) {
				String name = (String) values.get(1);
				List<String> columns = new ArrayList<>();
				Set<Integer> real = new HashSet<>();
				int alias = parseColumns(sql, columns, real);
				tables.put(name, new Table(name, root.intValue(), List.copyOf(columns), alias, Set.copyOf(real)));
			}
		});
	}

	/**
	 * Fills {@code columns} (and the indexes of columns with REAL affinity) from a CREATE TABLE statement; returns the
	 * index of an INTEGER PRIMARY KEY column or -1.
	 */
	static int parseColumns(String sql, List<String> columns, Set<Integer> realColumns) {
		int open = sql.indexOf('(');
		int close = sql.lastIndexOf(')');
		if (open < 0 || close <= open) {
			return -1;
		}
		int alias = -1;
		for (String part : splitTopLevel(sql.substring(open + 1, close))) {
			String definition = part.strip();
			String upper = definition.toUpperCase(Locale.ROOT);
			if (definition.isEmpty() || upper.startsWith("PRIMARY KEY") || upper.startsWith("UNIQUE") || upper.startsWith("FOREIGN KEY")
				|| upper.startsWith("CHECK") || upper.startsWith("CONSTRAINT")) {
				continue;
			}
			String name = firstToken(definition);
			String rest = upper.substring(Math.min(upper.length(), name.length())).strip();
			if (rest.startsWith("INTEGER") && rest.contains("PRIMARY KEY") && !rest.contains("DESC")) {
				alias = columns.size();
			}
			String type = rest.split("\\s+(PRIMARY|NOT|NULL|DEFAULT|UNIQUE|CHECK|REFERENCES|COLLATE|GENERATED|CONSTRAINT)\\b", 2)[0];
			if (!type.contains("INT") && !type.contains("CHAR") && !type.contains("CLOB") && !type.contains("TEXT")
				&& (type.contains("REAL") || type.contains("FLOA") || type.contains("DOUB"))) {
				realColumns.add(columns.size());
			}
			columns.add(unquote(name));
		}
		return alias;
	}

	private static List<String> splitTopLevel(String text) {
		List<String> parts = new ArrayList<>();
		int depth = 0;
		char quote = 0;
		int start = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quote != 0) {
				if (c == quote) {
					quote = 0;
				}
			} else if (c == '\'' || c == '"' || c == '`') {
				quote = c;
			} else if (c == '[') {
				quote = ']';
			} else if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
			} else if (c == ',' && depth == 0) {
				parts.add(text.substring(start, i));
				start = i + 1;
			}
		}
		parts.add(text.substring(start));
		return parts;
	}

	private static String firstToken(String definition) {
		char first = definition.charAt(0);
		char end = first == '"' ? '"' : first == '`' ? '`' : first == '[' ? ']' : 0;
		if (end != 0) {
			int close = definition.indexOf(end, 1);
			return close > 0 ? definition.substring(0, close + 1) : definition;
		}
		int i = 0;
		while (i < definition.length() && !Character.isWhitespace(definition.charAt(i))) {
			i++;
		}
		return definition.substring(0, i);
	}

	private static String unquote(String name) {
		if (name.length() >= 2 && (name.startsWith("\"") || name.startsWith("`") || name.startsWith("["))) {
			return name.substring(1, name.length() - 1);
		}
		return name;
	}

	// ------------------------------------------------------------------------------------------------ pages

	private interface CellVisitor {
		void visit(long rowid, byte[] payload) throws IOException;
	}

	private byte[] page(int number) throws IOException {
		byte[] fromWal = walPages.get(number);
		if (fromWal != null) {
			return fromWal;
		}
		long offset = (long) (number - 1) * pageSize;
		if (number < 1 || offset + pageSize > db.length) {
			throw new IOException("Damaged SQLite database (page " + number + " is missing).");
		}
		byte[] page = new byte[pageSize];
		System.arraycopy(db, (int) offset, page, 0, pageSize);
		return page;
	}

	private void walk(int number, Set<Integer> visited, CellVisitor visitor) throws IOException {
		if (!visited.add(number)) {
			throw new IOException("Damaged SQLite database (page loop).");
		}
		byte[] page = page(number);
		int header = number == 1 ? 100 : 0;
		int type = page[header] & 0xFF;
		int cells = u16(page, header + 3);
		if (type == 0x0D) {
			for (int i = 0; i < cells; i++) {
				int[] pos = {u16(page, header + 8 + 2 * i)};
				long payloadSize = varint(page, pos);
				long rowid = varint(page, pos);
				visitor.visit(rowid, payload(page, pos[0], payloadSize));
			}
		} else if (type == 0x05) {
			for (int i = 0; i < cells; i++) {
				int cell = u16(page, header + 12 + 2 * i);
				walk(u32(page, cell), visited, visitor);
			}
			walk(u32(page, header + 8), visited, visitor);
		} else {
			throw new IOException("Damaged SQLite database (unexpected page type " + type + ").");
		}
	}

	/** Reads a table-leaf payload, following overflow pages. */
	private byte[] payload(byte[] page, int start, long size) throws IOException {
		if (size < 0 || size > MAX_FILE_SIZE) {
			throw new IOException("Damaged SQLite database (record size).");
		}
		int total = (int) size;
		int maxLocal = usableSize - 35;
		int local;
		if (total <= maxLocal) {
			local = total;
		} else {
			int minLocal = ((usableSize - 12) * 32 / 255) - 23;
			int k = minLocal + ((total - minLocal) % (usableSize - 4));
			local = k <= maxLocal ? k : minLocal;
		}
		byte[] out = new byte[total];
		System.arraycopy(page, start, out, 0, local);
		int filled = local;
		int next = filled < total ? u32(page, start + local) : 0;
		Set<Integer> seen = new HashSet<>();
		while (filled < total) {
			if (next == 0 || !seen.add(next)) {
				throw new IOException("Damaged SQLite database (overflow chain).");
			}
			byte[] overflow = page(next);
			int chunk = Math.min(usableSize - 4, total - filled);
			System.arraycopy(overflow, 4, out, filled, chunk);
			filled += chunk;
			next = u32(overflow, 0);
		}
		return out;
	}

	// ------------------------------------------------------------------------------------------------ records

	static List<Object> decodeRecord(byte[] record) throws IOException {
		int[] pos = {0};
		long headerSize = varint(record, pos);
		List<Long> types = new ArrayList<>();
		while (pos[0] < headerSize) {
			types.add(varint(record, pos));
		}
		pos[0] = (int) headerSize;
		List<Object> values = new ArrayList<>(types.size());
		for (long type : types) {
			int p = pos[0];
			Object value;
			int length;
			if (type == 0) {
				value = null;
				length = 0;
			} else if (type >= 1 && type <= 6) {
				length = type == 5 ? 6 : type == 6 ? 8 : (int) type;
				long v = record[p];
				for (int i = 1; i < length; i++) {
					v = (v << 8) | (record[p + i] & 0xFF);
				}
				value = v;
			} else if (type == 7) {
				length = 8;
				long bits = 0;
				for (int i = 0; i < 8; i++) {
					bits = (bits << 8) | (record[p + i] & 0xFF);
				}
				value = Double.longBitsToDouble(bits);
			} else if (type == 8 || type == 9) {
				value = type - 8;
				length = 0;
			} else if (type >= 12) {
				length = (int) ((type - (type % 2 == 0 ? 12 : 13)) / 2);
				if (p + length > record.length) {
					throw new IOException("Damaged SQLite record.");
				}
				value = type % 2 == 0 ? java.util.Arrays.copyOfRange(record, p, p + length) : new String(record, p, length, StandardCharsets.UTF_8);
			} else {
				throw new IOException("Damaged SQLite record (serial type " + type + ").");
			}
			pos[0] = p + length;
			values.add(value);
		}
		return values;
	}

	static long varint(byte[] data, int[] pos) {
		long value = 0;
		for (int i = 0; i < 9; i++) {
			int b = data[pos[0]++] & 0xFF;
			if (i == 8) {
				return (value << 8) | b;
			}
			value = (value << 7) | (b & 0x7F);
			if ((b & 0x80) == 0) {
				return value;
			}
		}
		return value;
	}

	// ------------------------------------------------------------------------------------------------ WAL

	/** Pages of every transaction committed in the WAL, newest version of each page. */
	private Map<Integer, byte[]> readWal(byte[] wal) {
		Map<Integer, byte[]> committed = new HashMap<>();
		if (wal.length < 32) {
			return committed;
		}
		int magic = u32(wal, 0);
		if ((magic != WAL_MAGIC_LE && magic != WAL_MAGIC_BE) || u32(wal, 8) != pageSize) {
			return committed;
		}
		boolean bigEndian = magic == WAL_MAGIC_BE;
		int[] sum = checksum(wal, 0, 24, new int[] {0, 0}, bigEndian);
		if (sum[0] != u32(wal, 24) || sum[1] != u32(wal, 28)) {
			return committed;
		}
		int salt1 = u32(wal, 16);
		int salt2 = u32(wal, 20);
		Map<Integer, byte[]> pending = new HashMap<>();
		int frameSize = 24 + pageSize;
		for (int offset = 32; offset + frameSize <= wal.length; offset += frameSize) {
			if (u32(wal, offset + 8) != salt1 || u32(wal, offset + 12) != salt2) {
				break;
			}
			sum = checksum(wal, offset, 8, sum, bigEndian);
			sum = checksum(wal, offset + 24, pageSize, sum, bigEndian);
			if (sum[0] != u32(wal, offset + 16) || sum[1] != u32(wal, offset + 20)) {
				break;
			}
			byte[] page = new byte[pageSize];
			System.arraycopy(wal, offset + 24, page, 0, pageSize);
			pending.put(u32(wal, offset), page);
			if (u32(wal, offset + 4) != 0) {
				committed.putAll(pending);
				pending.clear();
			}
		}
		return committed;
	}

	private static int[] checksum(byte[] data, int offset, int length, int[] previous, boolean bigEndian) {
		int s0 = previous[0];
		int s1 = previous[1];
		for (int i = offset; i < offset + length; i += 8) {
			s0 += word(data, i, bigEndian) + s1;
			s1 += word(data, i + 4, bigEndian) + s0;
		}
		return new int[] {s0, s1};
	}

	private static int word(byte[] data, int offset, boolean bigEndian) {
		return bigEndian ? u32(data, offset)
			: (data[offset] & 0xFF) | (data[offset + 1] & 0xFF) << 8 | (data[offset + 2] & 0xFF) << 16 | (data[offset + 3] & 0xFF) << 24;
	}

	// ------------------------------------------------------------------------------------------------ bytes

	private static int u16(byte[] data, int offset) {
		return (data[offset] & 0xFF) << 8 | (data[offset + 1] & 0xFF);
	}

	private static int u32(byte[] data, int offset) {
		return (data[offset] & 0xFF) << 24 | (data[offset + 1] & 0xFF) << 16 | (data[offset + 2] & 0xFF) << 8 | (data[offset + 3] & 0xFF);
	}

	private static boolean startsWith(byte[] data, byte[] prefix) {
		for (int i = 0; i < prefix.length; i++) {
			if (data[i] != prefix[i]) {
				return false;
			}
		}
		return true;
	}
}

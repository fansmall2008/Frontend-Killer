import struct

path = r"C:\Users\fansm\Desktop\libretro-database-1.22.1\rdb\Nintendo - Super Nintendo Entertainment System.rdb"
data = open(path, "rb").read()
print("file size:", len(data))
print("first 32 bytes:", data[:32].hex(" "))
print("magic:", data[:8])
print("bytes[8:12] BE:", struct.unpack(">I", data[8:12])[0])
print("bytes[12:16] BE:", struct.unpack(">I", data[12:16])[0])

def parse_entry(buf):
    fields = buf.split(b"\x00")
    out = {}
    j = 0
    while j + 1 < len(fields):
        k = fields[j]; v = fields[j+1]
        if k == b"":
            j += 1; continue
        out[k.decode("latin-1")] = v.decode("latin-1")
        j += 2
    return out

for start in (8, 12):
    print(f"\n=== walk from offset {start} ===")
    off = start; ok = True; count = 0
    for _ in range(5):
        if off + 4 > len(data):
            ok = False; break
        size = struct.unpack(">I", data[off:off+4])[0]
        print(f"  entry size={size}")
        if size <= 0 or size > 100000:
            ok = False; print("   implausible, abort"); break
        body = data[off+4:off+4+size]
        e = parse_entry(body)
        print("   keys:", list(e.keys()))
        print("   name:", e.get("name"))
        off += 4 + size; count += 1
    print("  walked", count, "entries, next off", off, "OK" if ok else "BAD")

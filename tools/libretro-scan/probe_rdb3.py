import struct

path = r"C:\Users\fansm\Desktop\libretro-database-1.22.1\rdb\Nintendo - Super Nintendo Entertainment System.rdb"
data = open(path, "rb").read()

# Header: "RARCHDB\0"(8) + u32(0) + u32 total_size(=len-10)
# Then a stream of typed items. Item header byte: type = b>>5, ln = b&0x1f
# if ln==0x1f -> extended length: read u32 BE following? test variants.

def read_items(data, start):
    off = start
    items = []
    while off < len(data):
        b = data[off]; off += 1
        typ = b >> 5
        ln = b & 0x1f
        if ln == 0x1f:
            # extended length: try 4-byte BE
            ln = struct.unpack(">I", data[off:off+4])[0]; off += 4
        val = data[off:off+ln]; off += ln
        items.append((typ, ln, val))
    return items, off

# We expect a flat stream of key/value text items, with occasional binary (crc/md5/sha1).
# Try to reconstruct key->value dict per entry, splitting on 'name' key occurrences.
items, endoff = read_items(data, 10)
print("total items:", len(items), "endoff:", endoff, "filelen:", len(data))
from collections import Counter
print("type histogram:", Counter(t for t,_,_ in items))
# print first 20 items decoded
for i,(t,l,v) in enumerate(items[:24]):
    try: s = v.decode('utf-8')
    except: s = v.hex()
    print(f"  [{i}] type={t} len={l} val={s!r}")

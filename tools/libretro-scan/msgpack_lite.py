import struct

class Reader:
    def __init__(self, buf):
        self.b = buf; self.i = 0
    def u8(self):
        v = self.b[self.i]; self.i += 1; return v
    def take(self, n):
        v = self.b[self.i:self.i+n]; self.i += n; return v
    def be(self, n):
        return int.from_bytes(self.take(n), 'big')

def decode(r):
    """Return (kind, value). kind in {'map','str','bin','int','uint','nil','bool','float','other'}"""
    c = r.u8()
    if c <= 0x7f: return ('uint', c)
    if 0x80 <= c <= 0x8f: return ('map', decode_map(r, c & 0x0f))
    if 0x90 <= c <= 0x9f: return ('array', decode_arr(r, c & 0x0f))
    if 0xa0 <= c <= 0xbf: return ('str', r.take(c & 0x1f))
    if c == 0xc0: return ('nil', None)
    if c == 0xc1: raise ValueError("never used")
    if c == 0xc2: return ('bool', False)
    if c == 0xc3: return ('bool', True)
    if c == 0xc4: return ('bin', r.take(r.be(1)))
    if c == 0xc5: return ('bin', r.take(r.be(2)))
    if c == 0xc6: return ('bin', r.take(r.be(4)))
    if c == 0xc7: n=r.be(1); return ('ext', r.take(n))
    if c == 0xc8: n=r.be(2); return ('ext', r.take(n))
    if c == 0xc9: n=r.be(4); return ('ext', r.take(n))
    if c == 0xca: return ('float', struct.unpack('>f', r.take(4))[0])
    if c == 0xcb: return ('float', struct.unpack('>d', r.take(8))[0])
    if c == 0xcc: return ('uint', r.be(1))
    if c == 0xcd: return ('uint', r.be(2))
    if c == 0xce: return ('uint', r.be(4))
    if c == 0xcf: return ('uint', r.be(8))
    if c == 0xd0: return ('int', -(-r.be(1) & 0xff) if r.b else 0)
    if c == 0xd0: 
        v=r.be(1); return ('int', v-256 if v>127 else v)
    if c == 0xd1: v=r.be(2); return ('int', v-65536 if v>32767 else v)
    if c == 0xd2: v=r.be(4); return ('int', v-(1<<32) if v>(1<<31) else v)
    if c == 0xd3: v=r.be(8); return ('int', v-(1<<64) if v>(1<<63) else v)
    if c == 0xd9: return ('str', r.take(r.be(1)))
    if c == 0xda: return ('str', r.take(r.be(2)))
    if c == 0xdb: return ('str', r.take(r.be(4)))
    if c == 0xdc: return ('array', decode_arr(r, r.be(2)))
    if c == 0xdd: return ('array', decode_arr(r, r.be(4)))
    if c == 0xde: return ('map', decode_map(r, r.be(2)))
    if c == 0xdf: return ('map', decode_map(r, r.be(4)))
    if 0xe0 <= c <= 0xff: return ('int', c-256)
    raise ValueError(f"unknown msgpack byte 0x{c:02x} at {r.i-1}")

def decode_map(r, n):
    d = {}
    for _ in range(n):
        _, key = decode(r)
        kv, val = decode(r)
        if isinstance(key, bytes): key = key.decode('utf-8', 'replace')
        d[key] = val
    return d

def decode_arr(r, n):
    return [decode(r)[1] for _ in range(n)]

def parse_rdb(path):
    data = open(path, 'rb').read()
    assert data[:7] == b'RARCHDB', data[:7]
    meta_off = int.from_bytes(data[8:16], 'big')
    r = Reader(data)
    r.i = 16
    entries = []
    while True:
        kind, val = decode(r)
        if kind == 'nil':
            break
        if kind == 'map':
            entries.append(val)
        else:
            raise ValueError(f"unexpected top-level {kind}")
    return entries, meta_off, len(data)

if __name__ == "__main__":
    path = r"C:\Users\fansm\Desktop\libretro-database-1.22.1\rdb\Nintendo - Super Nintendo Entertainment System.rdb"
    entries, meta_off, flen = parse_rdb(path)
    print("entries:", len(entries), "meta_off:", meta_off, "filelen:", flen)
    print("first entry keys:", list(entries[0].keys()))
    for e in entries[:3]:
        print({k:(v.hex() if isinstance(v,bytes) else v) for k,v in e.items()})
    # show union of all keys
    from collections import Counter
    kc = Counter()
    for e in entries: kc.update(e.keys())
    print("key coverage:", dict(kc))

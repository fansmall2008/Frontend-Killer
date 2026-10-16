"""Build a standalone SQLite analysis DB on the Desktop from libretro-database.
Two tables: dat_entries (clrmamepro DAT text) and rdb_entries (RARCHDB=MessagePack).
Never touches the project database.
"""
import os, re, sqlite3, sys
from msgpack_lite import Reader, decode as mp_decode

BASE = r"C:\Users\fansm\Desktop\libretro-database-1.22.1"
OUT_DB = r"C:\Users\fansm\Desktop\libretro-scan\libretro_scan.db"

# ---------- msgpack str -> text fix ----------
def mp_decode2(r):
    kind, val = mp_decode(r)
    if kind == 'str' and isinstance(val, (bytes, bytearray)):
        val = val.decode('utf-8', 'replace')
    return kind, val

def parse_rdb_entries(path):
    data = open(path, 'rb').read()
    if data[:7] != b'RARCHDB':
        return None
    r = Reader(data); r.i = 16
    out = []
    n = len(data)
    while r.i < n:
        c = data[r.i]
        if c == 0xc0:  # NIL sentinel -> end of records
            break
        kind, val = mp_decode2(r)
        if kind == 'map':
            out.append(val)
        else:
            break
    return out

# ---------- clrmamepro DAT parser ----------
def _scan_block(txt, start):
    """txt[start] is just after an opening '('; return (inner_text, index_after_closing)."
    Quote-aware and depth-aware."""
    depth = 1; j = start; inq = False; L = len(txt)
    while j < L:
        ch = txt[j]
        if ch == '"':
            inq = not inq
        elif not inq:
            if ch == '(': depth += 1
            elif ch == ')':
                depth -= 1
                if depth == 0: break
        j += 1
    return txt[start:j], j+1

def parse_dat(path):
    txt = open(path, 'r', encoding='utf-8', errors='replace').read()
    games = []
    L = len(txt)
    for m in re.finditer(r'\bgame\s*\(', txt):
        block, _ = _scan_block(txt, m.end())
        g = parse_game_block(block)
        if g: games.append(g)
    return games

def parse_game_block(block):
    def field(name):
        mm = re.search(r'\b'+name+r'\s+"((?:[^"\\]|\\.)*)"', block)
        return mm.group(1) if mm else None
    name = field('name')
    region = field('region')
    serial = field('serial')
    comment = field('comment')
    # rom blocks: quote-aware scan of each 'rom ('
    roms = []
    for rm in re.finditer(r'\brom\s*\(', block):
        rb, _ = _scan_block(block, rm.end())
        nm = re.search(r'\bname\s+"((?:[^"\\]|\\.)*)"', rb)
        cm = re.search(r'\bcrc\s+([0-9A-Fa-f]+)', rb)
        md5m = re.search(r'\bmd5\s+([0-9A-Fa-f]+)', rb)
        sha1m = re.search(r'\bsha1\s+([0-9A-Fa-f]+)', rb)
        sizem = re.search(r'\bsize\s+(\d+)', rb)
        roms.append(dict(rom_name=nm.group(1) if nm else None, crc=(cm.group(1).upper() if cm else None),
                         md5=md5m.group(1).upper() if md5m else None,
                         sha1=sha1m.group(1).upper() if sha1m else None,
                         size=int(sizem.group(1)) if sizem else None))
    if not roms:
        roms = [dict(rom_name=None, crc=None, md5=None, sha1=None, size=None)]
    return dict(name=name, region=region, serial=serial, comment=comment, roms=roms)

# ---------- build ----------
def main():
    if os.path.exists(OUT_DB):
        os.remove(OUT_DB)
    con = sqlite3.connect(OUT_DB)
    cur = con.cursor()
    cur.execute("""CREATE TABLE dat_entries(
        id INTEGER PRIMARY KEY, source TEXT, system TEXT, name TEXT,
        region TEXT, serial TEXT, comment TEXT, rom_name TEXT,
        crc TEXT, md5 TEXT, sha1 TEXT, size INTEGER)""")
    cur.execute("""CREATE TABLE rdb_entries(
        id INTEGER PRIMARY KEY, system TEXT, name TEXT, description TEXT,
        rom_name TEXT, crc TEXT, md5 TEXT, sha1 TEXT, serial TEXT, region TEXT,
        size INTEGER, releasemonth INTEGER, releaseyear INTEGER, genre TEXT,
        publisher TEXT, developer TEXT, users TEXT, franchise TEXT,
        esrb_rating TEXT, raw_keys TEXT)""")
    cur.execute("CREATE INDEX idx_dat_crc ON dat_entries(crc)")
    cur.execute("CREATE INDEX idx_rdb_crc ON rdb_entries(crc)")
    cur.execute("CREATE INDEX idx_dat_sys ON dat_entries(system)")
    cur.execute("CREATE INDEX idx_rdb_sys ON rdb_entries(system)")

    dat_rows = 0; rdb_rows = 0
    # DAT sources
    for sub, tag in [('metadat/no-intro','no-intro'), ('metadat/redump','redump'),
                     ('metadat/serial','serial'), ('dat','dat')]:
        d = os.path.join(BASE, sub)
        if not os.path.isdir(d): continue
        for fn in sorted(os.listdir(d)):
            if not fn.lower().endswith('.dat'): continue
            system = os.path.splitext(fn)[0]
            try:
                games = parse_dat(os.path.join(d, fn))
            except Exception as e:
                print("DAT parse err", fn, e); continue
            for g in games:
                for rom in g['roms']:
                    cur.execute("INSERT INTO dat_entries(source,system,name,region,serial,comment,rom_name,crc,md5,sha1,size) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                        (tag, system, g['name'], g['region'], g['serial'], g['comment'],
                         rom['rom_name'], rom['crc'], rom['md5'], rom['sha1'], rom['size']))
                    dat_rows += 1
    # RDB
    rdbdir = os.path.join(BASE, 'rdb')
    for fn in sorted(os.listdir(rdbdir)):
        if not fn.lower().endswith('.rdb'): continue
        system = os.path.splitext(fn)[0]
        try:
            entries = parse_rdb_entries(os.path.join(rdbdir, fn))
        except Exception as e:
            print("RDB parse err", fn, e); continue
        if entries is None: continue
        def hexb(v):
            if isinstance(v,(bytes,bytearray)): return v.hex().upper()
            return None
        def txt(v):
            if isinstance(v,(bytes,bytearray)): return v.decode('utf-8','replace')
            return v
        for e in entries:
            crc = hexb(e.get('crc'))
            if crc is None and isinstance(e.get('crc'), str): crc = e.get('crc').upper()
            cur.execute("INSERT INTO rdb_entries(system,name,description,rom_name,crc,md5,sha1,serial,region,size,releasemonth,releaseyear,genre,publisher,developer,users,franchise,esrb_rating,raw_keys) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (system, txt(e.get('name')), txt(e.get('description')), txt(e.get('rom_name')),
                 crc, hexb(e.get('md5')), hexb(e.get('sha1')), txt(e.get('serial')), txt(e.get('region')),
                 e.get('size'), e.get('releasemonth'), e.get('releaseyear'),
                 txt(e.get('genre')), txt(e.get('publisher')), txt(e.get('developer')),
                 str(e.get('users')) if e.get('users') is not None else None,
                 txt(e.get('franchise')), txt(e.get('esrb_rating')),
                 ','.join(sorted(e.keys()))))
            rdb_rows += 1
    con.commit()
    print(f"DONE dat_rows={dat_rows} rdb_rows={rdb_rows} db={OUT_DB}")
    con.close()

if __name__ == '__main__':
    main()

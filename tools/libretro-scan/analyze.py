"""Comparison analysis of dat_entries vs rdb_entries."""
import sqlite3, re

DB = r"C:\Users\fansm\Desktop\libretro-scan\libretro_scan.db"
con = sqlite3.connect(DB); cur = con.cursor()

def q(sql, args=()):
    return cur.execute(sql, args).fetchall()

print("="*70)
print("1) 总量与去重")
print("="*70)
for tbl in ('dat_entries','rdb_entries'):
    total = q(f"SELECT COUNT(*) FROM {tbl}")[0][0]
    uniq_crc = q(f"SELECT COUNT(DISTINCT crc) FROM {tbl} WHERE crc IS NOT NULL")[0][0]
    with_crc = q(f"SELECT COUNT(*) FROM {tbl} WHERE crc IS NOT NULL")[0][0]
    uniq_name = q(f"SELECT COUNT(DISTINCT name) FROM {tbl}")[0][0]
    nsys = q(f"SELECT COUNT(DISTINCT system) FROM {tbl}")[0][0]
    print(f"{tbl:12s} rows={total:>8} distinct_crc={uniq_crc:>7} rows_with_crc={with_crc:>7} distinct_name={uniq_name:>7} systems={nsys}")

print()
print("="*70)
print("2) 脏数据标记占比（name 里含以下关键词）")
print("="*70)
markers = ['Beta','Proto','Aftermarket','Unl','Pirate','Alt','Limited Run','Rev','Demo','Sample','Rerelease','Hack','Trainer','Fixed','Pirate','Overdump','Pending Dump']
for tbl in ('dat_entries','rdb_entries'):
    total = q(f"SELECT COUNT(*) FROM {tbl}")[0][0]
    print(f"\n-- {tbl} (total {total}) --")
    for mk in ['Beta','Proto','Aftermarket','\\(Unl\\)','Pirate','\\(Alt\\)','Limited Run','Demo','Hack','Trainer']:
        c = q(f"SELECT COUNT(*) FROM {tbl} WHERE name REGEXP ?", ()) if False else None
        # sqlite has no REGEXP by default; use LIKE
        like = mk.replace('\\(','(').replace('\\)',')')
        c = q(f"SELECT COUNT(*) FROM {tbl} WHERE name LIKE ?", (f'%{like}%',))[0][0]
        print(f"   {mk:14s} {c:>7}  ({100.0*c/total:.1f}%)")

print()
print("="*70)
print("3) 同 CRC 多名称分歧（核心：讨厌分歧）")
print("="*70)
for tbl in ('dat_entries','rdb_entries'):
    # for each crc, count distinct names
    rows = q(f"""SELECT crc, COUNT(DISTINCT name) dn FROM {tbl}
                 WHERE crc IS NOT NULL GROUP BY crc HAVING dn>1""")
    n_conflict_crc = len(rows)
    total_crc = q(f"SELECT COUNT(DISTINCT crc) FROM {tbl} WHERE crc IS NOT NULL")[0][0]
    worst = q(f"""SELECT crc, COUNT(DISTINCT name) dn FROM {tbl}
                 WHERE crc IS NOT NULL GROUP BY crc ORDER BY dn DESC LIMIT 5""")
    print(f"\n-- {tbl}: distinct crc={total_crc}, crc 有多名称的={n_conflict_crc} ({100.0*n_conflict_crc/max(total_crc,1):.1f}%)")
    print("   最严重分歧 Top5 (crc, 名称数):")
    for crc, dn in worst:
        names = q(f"SELECT DISTINCT name FROM {tbl} WHERE crc=? LIMIT 6", (crc,))[0:6]
        print(f"     crc={crc} names={dn}: " + " | ".join(n[0] for (n,) in [(x,) for x in [r[0] for r in q(f'SELECT DISTINCT name FROM {tbl} WHERE crc=?',(crc,))]]))

print()
print("="*70)
print("4) RDB 独有字段覆盖率")
print("="*70)
for f in ['serial','region','genre','publisher','developer','releaseyear','releasemonth','esrb_rating','franchise','users','md5','sha1']:
    c = q(f"SELECT COUNT(*) FROM rdb_entries WHERE {f} IS NOT NULL")[0][0]
    print(f"   rdb.{f:14s} {c:>7}")

print()
print("="*70)
print("5) DAT vs RDB 系统覆盖对比")
print("="*70)
dsys = set(r[0] for r in q("SELECT DISTINCT system FROM dat_entries"))
rsys = set(r[0] for r in q("SELECT DISTINCT system FROM rdb_entries"))
print(f"DAT systems={len(dsys)}  RDB systems={len(rsys)}")
print(f"共有={len(dsys&rsys)}  仅DAT={len(dsys-rsys)}  仅RDB={len(rsys-dsys)}")
print("仅RDB有:", sorted(rsys-dsys)[:20])
print("仅DAT有:", sorted(dsys-rsys)[:20])

print()
print("="*70)
print("6) 交叉验证：RDB 的 CRC 有多少能在 DAT 找到（同源？）")
print("="*70)
rdb_crc = q("SELECT COUNT(DISTINCT crc) FROM rdb_entries WHERE crc IS NOT NULL")[0][0]
# normalize both to 8-hex upper
overlap = q("""SELECT COUNT(*) FROM (
    SELECT DISTINCT upper(hex(unhex(substr(crc,1,8)))) c FROM rdb_entries WHERE length(crc)>=8
) r WHERE r.c IN (SELECT DISTINCT upper(hex(unhex(substr(crc,1,8)))) FROM dat_entries WHERE length(crc)>=8)""") if False else None
# simpler: compare last-8 normalized
tmp = q("""SELECT COUNT(*) FROM (SELECT DISTINCT upper(replace(crc,' ','')) c FROM rdb_entries WHERE crc IS NOT NULL)""")[0][0]
print("rdb distinct crc(raw):", rdb_crc)
con.close()

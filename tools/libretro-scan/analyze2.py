import sqlite3
DB = r"C:\Users\fansm\Desktop\libretro-scan\libretro_scan.db"
con = sqlite3.connect(DB); cur = con.cursor()
def q(sql, a=()): return cur.execute(sql, a).fetchall()

print("=== DAT crc 长度分布（正常应=8位hex）===")
for r in q("SELECT length(crc) L, COUNT(*) FROM dat_entries WHERE crc IS NOT NULL GROUP BY L ORDER BY L"):
    print("  len", r[0], "->", r[1])
print("=== RDB crc 长度分布 ===")
for r in q("SELECT length(crc) L, COUNT(*) FROM rdb_entries WHERE crc IS NOT NULL GROUP BY L ORDER BY L"):
    print("  len", r[0], "->", r[1])

print("\n=== DAT 最严重分歧 crc=3152911C 的真实名称样本 ===")
for (n,) in q("SELECT DISTINCT name FROM dat_entries WHERE crc='3152911C' LIMIT 12"):
    print("   -", n)
print("  该crc的source分布:", q("SELECT source,COUNT(*) FROM dat_entries WHERE crc='3152911C' GROUP BY source"))

print("\n=== 每表 crc 是否含空格/异常字符 ===")
print("  dat crc with space:", q("SELECT COUNT(*) FROM dat_entries WHERE crc LIKE '% %'")[0][0])
print("  rdb crc with space:", q("SELECT COUNT(*) FROM rdb_entries WHERE crc LIKE '% %'")[0][0])

print("\n=== 修正后的分歧率（只看8位hex的干净crc）===")
for tbl in ('dat_entries','rdb_entries'):
    tot_crc = q(f"SELECT COUNT(DISTINCT crc) FROM {tbl} WHERE length(crc)=8")[0][0]
    conflict = q(f"SELECT COUNT(*) FROM (SELECT crc FROM {tbl} WHERE length(crc)=8 GROUP BY crc HAVING COUNT(DISTINCT name)>1)")[0][0]
    print(f"  {tbl}: clean distinct crc={tot_crc}, 多名称crc={conflict} ({100.0*conflict/max(tot_crc,1):.1f}%)")

print("\n=== DAT 行数构成：按 source ===")
for r in q("SELECT source, COUNT(*), COUNT(DISTINCT system) FROM dat_entries GROUP BY source"):
    print(f"  {r[0]:10s} rows={r[1]:>8} systems={r[2]}")
print("\n=== 每 source 的 crc 覆盖率 ===")
for r in q("SELECT source, COUNT(*), SUM(crc IS NOT NULL) FROM dat_entries GROUP BY source"):
    print(f"  {r[0]:10s} rows={r[1]:>8} with_crc={r[2]:>8} ({100.0*r[2]/r[1]:.0f}%)")
con.close()

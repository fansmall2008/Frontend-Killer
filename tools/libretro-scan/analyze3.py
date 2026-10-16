"""Cross-check: rdb crc vs dat crc overlap (proves rdb is compiled from dat), and real divergence names."""
import sqlite3
DB = r"C:\Users\fansm\Desktop\libretro-scan\libretro_scan.db"
con = sqlite3.connect(DB); cur = con.cursor()
def q(sql, a=()): return cur.execute(sql, a).fetchall()

# normalize crc to 8-hex upper on both sides
q("DROP TABLE IF EXISTS _d8"); 
q("CREATE TEMP TABLE _d8 AS SELECT DISTINCT upper(substr(crc,-8)) c FROM dat_entries WHERE length(crc)>=8 AND crc IS NOT NULL")
q("CREATE TEMP TABLE _r8 AS SELECT DISTINCT upper(substr(crc,-8)) c FROM rdb_entries WHERE length(crc)=8 AND crc IS NOT NULL")

d_tot = q("SELECT COUNT(*) FROM _d8")[0][0]
r_tot = q("SELECT COUNT(*) FROM _r8")[0][0]
overlap = q("SELECT COUNT(*) FROM _r8 WHERE c IN (SELECT c FROM _d8)")[0][0]
print("=== CRC 交叉验证（8位归一）===")
print(f"  DAT distinct crc = {d_tot}")
print(f"  RDB distinct crc = {r_tot}")
print(f"  RDB 的 crc 能在 DAT 找到 = {overlap} ({100.0*overlap/r_tot:.1f}% of RDB)")
print(f"  → RDB 独有 crc = {r_tot-overlap}")

print("\n=== RDB 真实分歧样本（名称完整）===")
for crc, dn in q("""SELECT crc, COUNT(DISTINCT name) dn FROM rdb_entries
                    WHERE length(crc)=8 GROUP BY crc HAVING dn>1 ORDER BY dn DESC LIMIT 5"""):
    names = [r[0] for r in q("SELECT DISTINCT name FROM rdb_entries WHERE crc=? LIMIT 8", (crc,))]
    print(f"  crc={crc} names={dn}:")
    for nm in names: print("     -", nm)

print("\n=== DAT 最大分歧 crc=3152911C 的来源确认（是否为坏dump/empty）===")
print("  rom_name 样本:", q("SELECT DISTINCT rom_name FROM dat_entries WHERE crc='3152911C' LIMIT 5"))
print("  size 分布:", q("SELECT size, COUNT(*) FROM dat_entries WHERE crc='3152911C' GROUP BY size ORDER BY COUNT(*) DESC LIMIT 5"))
print("  source:", q("SELECT source, COUNT(*) FROM dat_entries WHERE crc='3152911C' GROUP BY source"))
con.close()

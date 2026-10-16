path = r"C:\Users\fansm\Desktop\libretro-database-1.22.1\rdb\Nintendo - Super Nintendo Entertainment System.rdb"
data = open(path, "rb").read()

# Dump region 10..400 as repr to see raw structure
print("=== bytes[10:400] repr ===")
print(repr(data[10:400]))

print("\n=== bytes[8:16] hex ===")
print(data[8:16].hex(" "))

# Count how many times 'name' appears as a null-delimited key token
import re
# find all occurrences of b'\x00name\x00'
idxs = [m.start() for m in re.finditer(re.escape(b"\x00name\x00"), data)]
print("\ncount of \\x00name\\x00 tokens:", len(idxs))
idxs2 = [m.start() for m in re.finditer(re.escape(b"name\x00"), data)]
print("count of name\\x00 tokens:", len(idxs2))

# Look at the two bytes right before first 'name' occurrence
if idxs:
    p = idxs[0]
    print("\nfirst \\x00name\\x00 at", p, "preceding 8 bytes:", data[p-8:p+1].hex(" "))

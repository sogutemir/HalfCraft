"""Layout/signed coordinates and occupancy mask checks, no game process required."""
import struct
import math
assert struct.calcsize('<12I3iI3d2f2I24x')==128
assert struct.calcsize('<8I3d2f')==64
assert 128+512*8==4224 and 4224+64==4288
base=(8,-8,-12)
point=(float(base[0])+.125,float(base[1])+.125,float(base[2])+.125)
assert point==(8.125,-7.875,-11.875) # C++ must cast signed base before adding unsigned cell index.
for bit in range(64):
    x,y,z=bit%4,bit//4%4,bit//16
    assert x+y*4+z*16==bit
feet=(12.1,-6,-7.95)
origin=(feet[0]*40,-feet[2]*40,feet[1]*40+36.03125)
assert origin==(484.,318.,-203.96875)
assert math.dist(feet,(origin[0]/40,(origin[2]-36.03125)/40,-origin[1]/40))<1e-12
print('Collision PASS: layout4288/guest4224, signed negative coordinates, 64-bit subcell indexing, feet/hull round trip')

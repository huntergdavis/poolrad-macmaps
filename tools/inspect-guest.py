#!/usr/bin/env python3
"""Read-only diagnostic for a private guest RAM capture; no game data embedded.

Reports the verified selected handle, party chain and WindowRecord geometry.
This is inspection tooling, not a replacement for production probe validation.
"""
import argparse
import json
from pathlib import Path
import struct


def inspect(path):
    b=path.read_bytes()
    def check(p,n):
        if p<0 or p+n>len(b): raise ValueError(f'Address out of bounds: {p:x}+{n}')
    def ptr(p):
        check(p,4); return struct.unpack_from('>I',b,p)[0]&0xffffff
    def word(p):
        check(p,2); return struct.unpack_from('>h',b,p)[0]
    def pascal(p):
        check(p,1); check(p+1,b[p]); return b[p+1:p+1+b[p]].decode('mac_roman')
    result={'capture':str(path),'application':pascal(0x910)}
    a5=ptr(0x904); result['a5']=hex(a5)
    if result['application']=='Pool of Radiance v1.1':
        selected=ptr(a5-0x51a2); h=ptr(a5-0x519e); seen=set(); rows=[]
        while h and h not in seen and len(seen)<71:
            seen.add(h); r=ptr(h); check(r,302)
            rows.append({'handle':hex(h),'record':hex(r),
                'name':b[r:r+16].split(b'\0')[0].decode('mac_roman'),
                'slot':b[r+0xc9],'selected':h==selected})
            h=ptr(r+0x110)
        result.update(selected_handle=hex(selected),party=rows,
                      selection_enabled=b[a5-0x6168],line_height=word(a5-0x5eb6))
    windows=[]; seen=set(); w=ptr(0x9d6)
    while w and w not in seen and len(seen)<64:
        seen.add(w); check(w,156)
        title=ptr(w+134); region=ptr(w+118)
        data={'window':hex(w),'kind':word(w+108),'visible':bool(b[w+110]),
              'title':pascal(ptr(title)) if title else ''}
        if region:
            r=ptr(region); check(r,10)
            data['content_tlbr']=[word(r+i) for i in (2,4,6,8)]
        windows.append(data); w=ptr(w+144)
    result['windows']=windows
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture',type=Path)
    print(json.dumps(inspect(parser.parse_args().capture),indent=2))

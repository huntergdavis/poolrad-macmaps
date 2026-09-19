#!/usr/bin/env python3
"""Read-only disassembly of a user-supplied Macintosh game CODE resource.

Requires capstone and macresources. No extracted game bytes are checked in.
Usage: disassemble-game.py RESOURCE_FORK CODE_ID START END
       disassemble-game.py RESOURCE_FORK CODE_ID --refs HEX_BYTES
Offsets are hexadecimal; CODE_ID is decimal. AppleDouble forks are accepted.
"""
import argparse
import struct
from pathlib import Path


def code_resource(path, code_id):
    from macresources import parse_file
    data = Path(path).read_bytes()
    if data[:4] == b'\x00\x05\x16\x07':
        for i in range(struct.unpack_from('>H', data, 24)[0]):
            kind, offset, length = struct.unpack_from('>III', data, 26 + i * 12)
            if kind == 2:
                if offset + length > len(data):
                    raise ValueError('Truncated resource fork')
                data = data[offset:offset + length]
                break
        else:
            raise ValueError('AppleDouble has no resource fork')
    return next(bytes(r.data) for r in parse_file(data)
                if r.type == b'CODE' and r.id == code_id)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('fork')
    parser.add_argument('code', type=int)
    parser.add_argument('start', nargs='?', default='0')
    parser.add_argument('end', nargs='?')
    parser.add_argument('--refs', help='find an instruction-aligned byte sequence')
    args = parser.parse_args()
    code = code_resource(args.fork, args.code)
    if args.refs:
        needle = bytes.fromhex(args.refs)
        for at in range(0, len(code) - len(needle) + 1, 2):
            if code[at:at + len(needle)] == needle:
                print(f'{at:04x}')
        return
    from capstone import Cs, CS_ARCH_M68K, CS_MODE_BIG_ENDIAN, CS_MODE_M68K_000
    start = int(args.start, 16)
    end = int(args.end, 16) if args.end else len(code)
    engine = Cs(CS_ARCH_M68K, CS_MODE_BIG_ENDIAN | CS_MODE_M68K_000)
    engine.skipdata = True
    for op in engine.disasm(code[start:end], start):
        print(f'{op.address:04x}  {op.mnemonic:10s} {op.op_str}')


if __name__ == '__main__':
    main()
